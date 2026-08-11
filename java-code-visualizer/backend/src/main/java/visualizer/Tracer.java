package visualizer;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Tracer
 * ------
 * Compiles a target .java file, launches it under the JDI debugger,
 * steps through every executed line, and records a JSON trace of:
 *   - the call stack (functions) with local variables at each step
 *   - the heap (objects/arrays) reachable from those locals
 *   - the current source line
 *   - accumulated stdout
 *
 * The resulting trace.json is consumed by the frontend visualizer.
 *
 * Usage:
 *   java visualizer.Tracer <path-to-source.java> <output-trace.json> [stdin-input-file] [max-steps]
 */
public class Tracer {

    // Fully-qualified name prefixes we don't step into (JDK internals, etc.)
    private static final String[] EXCLUDED_PREFIXES = {
            "java.*", "javax.*", "sun.*", "jdk.*", "com.sun.*"
    };

    private final List<Map<String, Object>> steps = new ArrayList<>();
    private final Set<Long> knownObjectIds = new HashSet<>();
    private final StringBuilder stdout = new StringBuilder();
    private int maxSteps = 400;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java visualizer.Tracer <source.java> <output-trace.json> [stdin-file] [max-steps]");
            System.exit(1);
        }
        String sourcePath = args[0];
        String outputPath = args[1];
        String stdinFile = (args.length > 2 && !args[2].isEmpty()) ? args[2] : null;
        int maxSteps = args.length > 3 ? Integer.parseInt(args[3]) : 400;

        Tracer tracer = new Tracer();
        tracer.maxSteps = maxSteps;
        tracer.run(sourcePath, outputPath, stdinFile);
    }

    public void run(String sourcePath, String outputPath, String stdinFile) throws Exception {
        File sourceFile = new File(sourcePath).getAbsoluteFile();
        String className = deriveClassName(sourceFile);
        String sourceText = Files.readString(sourceFile.toPath());
        File classDir = compile(sourceFile);

        VirtualMachine vm = launchVM(className, classDir);

        // Feed stdin to the child process, capture stdout, in background threads.
        Process process = vm.process();
        pumpStdin(process, stdinFile);
        Thread outPump = pumpStdout(process);

        EventRequestManager erm = vm.eventRequestManager();
        ClassPrepareRequest prepareRequest = erm.createClassPrepareRequest();
        prepareRequest.addClassFilter(className);
        prepareRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        prepareRequest.enable();

        boolean stepRequestCreated = false;
        EventQueue queue = vm.eventQueue();

        eventLoop:
        while (true) {
            EventSet eventSet = queue.remove();
            for (Event event : eventSet) {
                if (event instanceof ClassPrepareEvent) {
                    if (!stepRequestCreated) {
                        StepRequest stepRequest = erm.createStepRequest(
                                ((ClassPrepareEvent) event).thread(),
                                StepRequest.STEP_LINE,
                                StepRequest.STEP_INTO);
                        for (String excl : EXCLUDED_PREFIXES) {
                            stepRequest.addClassExclusionFilter(excl);
                        }
                        stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                        stepRequest.enable();
                        stepRequestCreated = true;
                    }
                } else if (event instanceof StepEvent) {
                    captureStep(((StepEvent) event).thread());
                    if (steps.size() >= maxSteps) {
                        System.err.println("Reached max step limit (" + maxSteps + "), stopping trace.");
                        break eventLoop;
                    }
                } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                    break eventLoop;
                }
            }
            eventSet.resume();
        }

        try {
            vm.resume();
        } catch (VMDisconnectedException ignored) {
        }
        process.waitFor();
        outPump.join(2000);

        writeTraceJson(outputPath, className, sourceText);
        System.err.println("Trace written to " + outputPath + " (" + steps.size() + " steps).");
    }

    // ---------- Compilation ----------

    private File compile(File sourceFile) throws IOException, InterruptedException {
        File classDir = Files.createTempDirectory("visualizer-classes").toFile();
        ProcessBuilder pb = new ProcessBuilder(
                "javac", "-g", "-d", classDir.getAbsolutePath(), sourceFile.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String compileOutput = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("Compilation failed:\n" + compileOutput);
        }
        return classDir;
    }

    private String deriveClassName(File sourceFile) throws IOException {
        String content = Files.readString(sourceFile.toPath());
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("public\\s+class\\s+(\\w+)")
                .matcher(content);
        if (m.find()) return m.group(1);
        String name = sourceFile.getName();
        return name.substring(0, name.lastIndexOf('.'));
    }

    // ---------- VM launch ----------

    private VirtualMachine launchVM(String className, File classDir) throws Exception {
        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("main").setValue(className);
        args.get("options").setValue("-cp " + classDir.getAbsolutePath());
        return connector.launch(args);
    }

    private void pumpStdin(Process process, String stdinFile) {
        new Thread(() -> {
            try (OutputStream os = process.getOutputStream()) {
                if (stdinFile != null) {
                    Files.copy(Paths.get(stdinFile), os);
                }
            } catch (IOException ignored) {
            }
        }).start();
    }

    private Thread pumpStdout(Process process) {
        Thread t = new Thread(() -> {
            try (InputStream is = process.getInputStream()) {
                byte[] buf = new byte[1024];
                int n;
                while ((n = is.read(buf)) != -1) {
                    synchronized (stdout) {
                        stdout.append(new String(buf, 0, n));
                    }
                }
            } catch (IOException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    // ---------- Step capture ----------

    private void captureStep(ThreadReference thread) {
        try {
            Map<Long, Object> heap = new LinkedHashMap<>();
            List<Map<String, Object>> frames = new ArrayList<>();

            for (StackFrame frame : thread.frames()) {
                Location loc = frame.location();
                if (isExcluded(loc.declaringType().name())) continue;

                Map<String, Object> frameJson = new LinkedHashMap<>();
                frameJson.put("method", loc.declaringType().name() + "." + loc.method().name());
                frameJson.put("line", loc.lineNumber());

                List<Map<String, Object>> locals = new ArrayList<>();
                try {
                    for (LocalVariable var : frame.visibleVariables()) {
                        Value v = frame.getValue(var);
                        Map<String, Object> localJson = new LinkedHashMap<>();
                        localJson.put("name", var.name());
                        localJson.put("value", serializeValue(v, heap, thread));
                        locals.add(localJson);
                    }
                } catch (AbsentInformationException ignored) {
                }
                frameJson.put("locals", locals);
                frames.add(frameJson);
            }

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("line", thread.frame(0).location().lineNumber());
            step.put("frames", frames);
            step.put("heap", heap);
            synchronized (stdout) {
                step.put("stdout", stdout.toString());
            }
            steps.add(step);
        } catch (IncompatibleThreadStateException e) {
            // Skip a step we can't inspect rather than aborting the whole trace.
        }
    }

    private boolean isExcluded(String typeName) {
        for (String prefix : EXCLUDED_PREFIXES) {
            String base = prefix.replace(".*", "");
            if (typeName.startsWith(base + ".") || typeName.equals(base)) return true;
        }
        return false;
    }

    /**
     * Serializes a JDI Value into a JSON-friendly Java object.
     * Primitives/strings become literals. Arrays and objects become
     * {"ref": id} pointers, with their real contents recorded into `heap`.
     */
    private Object serializeValue(Value v, Map<Long, Object> heap, ThreadReference thread) {
        if (v == null) return null;

        if (v instanceof StringReference) {
            return ((StringReference) v).value();
        }
        if (v instanceof PrimitiveValue) {
            if (v instanceof IntegerValue) return ((IntegerValue) v).value();
            if (v instanceof LongValue) return ((LongValue) v).value();
            if (v instanceof DoubleValue) return ((DoubleValue) v).value();
            if (v instanceof FloatValue) return ((FloatValue) v).value();
            if (v instanceof BooleanValue) return ((BooleanValue) v).value();
            if (v instanceof CharValue) return String.valueOf(((CharValue) v).value());
            if (v instanceof ByteValue) return ((ByteValue) v).value();
            if (v instanceof ShortValue) return ((ShortValue) v).value();
            return v.toString();
        }

        if (v instanceof ArrayReference) {
            ArrayReference arr = (ArrayReference) v;
            long id = arr.uniqueID();
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("ref", id);
            if (!knownObjectIds.contains(id) || true) { // always refresh contents (mutable)
                List<Object> elements = new ArrayList<>();
                for (Value elem : arr.getValues()) {
                    elements.add(serializeValue(elem, heap, thread));
                }
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("kind", "array");
                obj.put("type", arr.type().name());
                obj.put("elements", elements);
                heap.put(id, obj);
                knownObjectIds.add(id);
            }
            return ref;
        }

        if (v instanceof ObjectReference) {
            ObjectReference obj = (ObjectReference) v;
            long id = obj.uniqueID();
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("ref", id);

            ReferenceType type = obj.referenceType();
            String typeName = type.name();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("kind", "object");
            entry.put("type", typeName);

            if (isExcluded(typeName)) {
                // Library type (HashMap, ArrayList, etc.) - show a safe toString()
                // instead of walking private JDK-internal fields.
                entry.put("summary", safeToString(obj, thread));
            } else {
                // User-defined class - walk declared fields directly.
                Map<String, Object> fields = new LinkedHashMap<>();
                for (Field f : type.fields()) {
                    if (f.isStatic()) continue;
                    try {
                        fields.put(f.name(), serializeValue(obj.getValue(f), heap, thread));
                    } catch (Exception e) {
                        fields.put(f.name(), "<unreadable>");
                    }
                }
                entry.put("fields", fields);
            }
            heap.put(id, entry);
            return ref;
        }

        return String.valueOf(v);
    }

    /** Calls toString() on a library object inside the debuggee, safely. */
    private String safeToString(ObjectReference obj, ThreadReference thread) {
        try {
            ReferenceType rt = obj.referenceType();
            List<Method> methods = rt.methodsByName("toString", "()Ljava/lang/String;");
            if (methods.isEmpty()) return typeSimpleName(rt.name());
            Method toString = methods.get(0);
            Value result = obj.invokeMethod(thread, toString, Collections.emptyList(),
                    ObjectReference.INVOKE_SINGLE_THREADED);
            return result instanceof StringReference ? ((StringReference) result).value() : String.valueOf(result);
        } catch (Exception e) {
            return typeSimpleName(obj.referenceType().name());
        }
    }

    private String typeSimpleName(String fqName) {
        int i = fqName.lastIndexOf('.');
        return i >= 0 ? fqName.substring(i + 1) : fqName;
    }

    // ---------- JSON output ----------

    private void writeTraceJson(String outputPath, String className, String sourceText) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("className", className);
        root.put("source", sourceText);
        root.put("steps", steps);
        synchronized (stdout) {
            root.put("finalOutput", stdout.toString());
        }
        try (Writer w = new FileWriter(outputPath)) {
            Json.write(root, w);
        }
    }
}
