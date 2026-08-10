# Java Code Visualizer

A step-by-step execution visualizer for Java, inspired by tools like Python
Tutor and CodeChef's code visualizer. Runs your program under the real JVM
debugger (JDI), so the values you see are the actual runtime state — not a
simulation.

- **Currently executed line** and **next line to be executed** highlighted
  in the code panel
- **Functions panel** — live call stack with local variables per frame
  (recursion shows as multiple stacked frames, just like a real debugger)
- **Objects panel** — arrays and objects on the heap, shown by reference,
  matching how Java's memory model actually works (frames hold references,
  heap holds the data)
- **Step controls** — Start / Prev / Next / End, plus a scrub slider
- **Program output** — stdout captured as the program actually runs

Currently supports **Java only**. The trace format (`trace.json`) is
intentionally language-agnostic, so support for other languages can be
added later by writing a new tracer that emits the same JSON shape — the
frontend won't need to change.

## How it works

1. `backend/` compiles your target `.java` file and launches it under the
   [Java Debug Interface](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jdi/com/sun/jdi/package-summary.html)
   (JDI) — the same API real Java debuggers use.
2. It single-steps line by line, and at every step records the call stack,
   local variables, and heap objects into a JSON trace.
3. `frontend/` is a small static web page that loads that JSON and lets you
   scrub through the execution.

No external dependencies — pure JDK on the backend, plain HTML/CSS/JS on
the frontend.

## Requirements

- JDK 17+ (uses the `jdk.jdi` module, bundled with the JDK — not the JRE)

## Usage

```bash
cd backend
./run.sh target-code/Sample.java
```

This compiles the tracer (first run only), traces `Sample.java`, and
writes `frontend/trace.json`. Then open `frontend/index.html` in a browser
(or serve the `frontend/` folder with any static file server) to view it.

To trace your own program:

```bash
./run.sh /path/to/YourProgram.java
```

If your program reads from stdin (`Scanner`, etc.), pass an input file:

```bash
./run.sh /path/to/YourProgram.java sample-input.txt
```

You can also raise the step cap (default 400, to guard against tracing an
infinite loop forever):

```bash
./run.sh /path/to/YourProgram.java sample-input.txt 1000
```

## Project layout

```
java-code-visualizer/
  backend/
    src/main/java/visualizer/
      Tracer.java       # JDI-based step tracer
      Json.java         # tiny dependency-free JSON writer
    target-code/
      Sample.java       # example program to trace
    run.sh              # compile + trace in one command
  frontend/
    index.html
    style.css
    app.js
    sample-trace.json   # pre-generated trace for Sample.java, so the UI
                         # has something to show immediately
```

## Known limitations (first version)

- Library objects (`HashMap`, `ArrayList`, etc.) show their `toString()`
  rather than a fully expanded internal structure — expanding them
  properly (buckets, entries) is a good next step.
- Multi-threaded programs are traced on whichever thread JDI reports
  stepping events for; concurrent execution isn't visualized specially.
- No syntax highlighting yet in the code panel (line highlighting only).

## Roadmap

- [ ] Expand `HashMap` / `ArrayList` / `List` internals in the Objects panel
- [ ] Syntax highlighting in the code view
- [ ] Support for additional languages (Python via `sys.settrace`,
      JavaScript via the V8 inspector protocol, etc.) reusing the same
      `trace.json` schema and frontend
- [ ] Drag-and-drop `.java` file upload + in-browser compile (via a small
      backend server) instead of the CLI script
