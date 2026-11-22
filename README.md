# Java Code Visualizer

Paste Java code, run it with the real JVM debugger on your computer, and step through the execution visually. The page shows the current source line, call stack, local variables, heap objects, and program output.

## Run the visualizer

You need JDK 17 or later.

**Windows:** double-click `backend/run-server.bat`, or run it from Command Prompt. **macOS/Linux/WSL:** from the repository root, run:

```bash
bash backend/run-server.sh
```

Keep the terminal window open, then open [http://127.0.0.1:8765](http://127.0.0.1:8765). Paste a program named `Main` into the editor and choose **Run & visualize**. The local server compiles it, starts it under JDI, and returns a trace to the page. Your code stays on your computer.

The editor accepts a package-free public class (for example, `Main` or `Sample`). You can provide stdin in the optional **Program input** field. Traces stop at 400 steps or after 15 seconds. Use **Next** or the arrow keys to move through the recorded states.

## Use the hosted page with your local runner

GitHub Pages cannot run Java by itself. To run code from the hosted page:

1. Start the local runner with `backend/run-server.bat` (Windows) or `bash backend/run-server.sh` (macOS/Linux/WSL).
2. On the hosted page, choose **Connect local runner**. If your browser asks to let the page access the local network, allow it.
3. Paste code and choose **Run & visualize**.

The browser sends the source from the page to the runner at `127.0.0.1` on your computer. The runner accepts requests only from this project’s hosted origin and loopback pages. If browser local-network access is blocked, open `http://127.0.0.1:8765` directly instead.

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
