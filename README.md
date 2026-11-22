# Java Code Visualizer

Paste Java code, run it with the real JVM debugger on your computer, and step through the execution visually. The page shows the current source line, call stack, local variables, heap objects, and program output.

## Run the visualizer

You need JDK 17 or later.

**Windows:** double-click `backend/run-server.bat`, or run it from Command Prompt. **macOS/Linux/WSL:** from the repository root, run:

```bash
bash backend/run-server.sh
```

Keep the terminal window open, then open [http://127.0.0.1:8765](http://127.0.0.1:8765). Paste a program named `Main` into the editor and choose **Run & visualize**. The local server compiles it, starts it under JDI, and returns a trace to the page. Your code stays on your computer.

The editor accepts a package-free `public class Main` for now. You can provide stdin in the optional **Program input** field. Traces stop at 400 steps or after 15 seconds. Use **Next** or the arrow keys to move through the recorded states.

## Hosted demo

The GitHub Pages site is a static demo. It can replay the included example and traces opened as JSON files, but it cannot run Java on its own. To paste and execute code, start the local runner and open the local address above.

## Run a trace from the command line

The original CLI workflow is also available:

```bash
cd backend
./run.sh target-code/Sample.java
```

It writes `frontend/trace.json`; open that trace using **Open trace** in the visualizer.

## How it works

The backend compiles your source with `javac`, launches it in a child JVM, and uses the [Java Debug Interface (JDI)](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jdi/com/sun/jdi/package-summary.html) to capture real runtime state. The local web server serves the page and the trace endpoint on loopback (`127.0.0.1`). It does not expose a public code execution service.

## Current limitations

- The editor currently expects a package-free `public class Main`.
- The trace is capped at 400 steps and 15 seconds.
- JDK library objects such as `HashMap` and `ArrayList` are summarized with `toString()` rather than expanded.
- Concurrent threads are not shown as separate timelines.
- The source view highlights the executing line but does not yet include Java syntax coloring.
