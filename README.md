# Java Code Visualizer

A step-by-step execution visualizer for Java. It runs programs under the real JVM debugger (JDI), then lets you replay the resulting runtime state in a browser.

- **Current and next lines** highlighted as you step
- **Call stack** with local variables in every frame, including recursive calls
- **Heap view** for arrays and user-defined objects
- **Program output** captured while the program runs
- **Trace replay controls** with keyboard shortcuts, a step slider, and JSON file loading

The published frontend is a static site: it does not compile or execute Java in the browser. Generate a trace with the local JDK backend, then open it in the page. Your source and trace stay in your browser when using the hosted page.

## How it works

1. `backend/` compiles a `.java` file and launches it under the [Java Debug Interface](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jdi/com/sun/jdi/package-summary.html) (JDI).
2. The tracer records the call stack, local variables, reachable heap objects, current source line, and stdout into a JSON trace.
3. `frontend/` loads a trace and provides a step-by-step replay.

The backend uses the JDK; the frontend uses plain HTML, CSS, and JavaScript.

## Requirements

- JDK 17 or later (`jdk.jdi` is included with the JDK, not the JRE)

## Usage

```bash
cd backend
./run.sh target-code/Sample.java
```

This compiles the tracer on its first run, traces `Sample.java`, and writes `frontend/trace.json`. Serve the `frontend/` folder with any static file server, then open the page. The example trace is already included so you can explore the visualizer without running the backend.

To trace your own program:

```bash
./run.sh /path/to/YourProgram.java
```

If your program reads from stdin, pass an input file:

```bash
./run.sh /path/to/YourProgram.java sample-input.txt
```

The default trace limit is 400 steps. You can raise it with an optional third argument:

```bash
./run.sh /path/to/YourProgram.java sample-input.txt 1000
```

## Publish with GitHub Pages

The repository includes a GitHub Actions workflow that publishes the static `frontend/` folder. To enable it:

1. Merge the page and workflow changes into the default branch.
2. In the repository settings, open **Pages** and set the publishing source to **GitHub Actions**.
3. Push a change to `frontend/` or run **Deploy visualizer to GitHub Pages** from the Actions tab.

The workflow deploys the included sample trace, so visitors can try the page immediately. Users can load their own generated trace JSON with **Open trace** or by dropping a JSON file onto the visualizer.

## Project layout

```
backend/
  src/main/java/visualizer/
    Tracer.java
    Json.java
  target-code/
    Sample.java
  run.sh
frontend/
  index.html
  style.css
  app.js
  sample-trace.json
.github/workflows/
  pages.yml
experiments/
  structure-visualizer/   # Separate static code-blueprint prototype
```

## Current limitations

- JDK library objects such as `HashMap` and `ArrayList` are summarized with `toString()` rather than expanded field by field.
- Multi-threaded execution is not visualized specially.
- The code view highlights lines but does not yet provide Java syntax coloring.
- The hosted page replays traces; it does not compile or run Java code itself.
