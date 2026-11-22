(() => {
  const $ = (id) => document.getElementById(id);
  const codeView = $("codeView");
  const functionsPanel = $("functionsPanel");
  const objectsPanel = $("objectsPanel");
  const outputPanel = $("outputPanel");
  const stepStatus = $("stepStatus");
  const stepSlider = $("stepSlider");
  const classNameLabel = $("classNameLabel");
  const traceMessage = $("traceMessage");
  const frameCount = $("frameCount");
  const objectCount = $("objectCount");
  const buttons = {
    start: $("btnStart"),
    prev: $("btnPrev"),
    next: $("btnNext"),
    end: $("btnEnd")
  };
  const traceFileInput = $("traceFileInput");
  const editorInput = $("editorInput");
  const stdinInput = $("stdinInput");
  const editorMessage = $("editorMessage");
  const runButton = $("runCode");
  const runButtonLabel = $("runButtonLabel");
  const loadExampleButton = $("loadExample");
  const connectRunnerButton = $("connectRunner");
  const runnerStatus = $("runnerStatus");
  const runnerStatusText = $("runnerStatusText");

  let trace = null;
  let currentIndex = 0;
  let demoTrace = null;
  const isHostedPage = location.hostname === "akarupusaieshwar-creator.github.io";
  const runnerBase = isHostedPage || location.protocol === "file:" ? "http://127.0.0.1:8765" : location.origin;
  let runnerConnected = location.hostname === "127.0.0.1" || location.hostname === "localhost";
  if (runnerConnected) setRunnerStatus(true, "Local runner connected");

  function validateTrace(data) {
    if (!data || typeof data !== "object" || typeof data.source !== "string" || !Array.isArray(data.steps)) {
      throw new Error("This file doesn't look like a Java Visualizer trace. Choose a trace JSON file.");
    }
    if (data.steps.some((step) => !step || !Number.isFinite(Number(step.line)))) {
      throw new Error("The trace has an invalid execution step.");
    }
    return {
      className: typeof data.className === "string" ? data.className : "Java program",
      source: data.source,
      steps: data.steps,
      finalOutput: typeof data.finalOutput === "string" ? data.finalOutput : "",
      truncated: data.truncated === true,
      timedOut: data.timedOut === true
    };
  }

  function showError(message) {
    traceMessage.textContent = message;
    traceMessage.parentElement.classList.add("has-error");
  }

  function setMessage(message) {
    traceMessage.textContent = message;
    traceMessage.parentElement.classList.remove("has-error");
  }

  function loadTrace(data, sourceName) {
    const parsed = validateTrace(data);
    trace = parsed;
    currentIndex = 0;
    classNameLabel.textContent = sourceName || parsed.className + ".java";
    classNameLabel.title = parsed.className;
    drawSource(parsed.source);
    stepSlider.max = Math.max(parsed.steps.length - 1, 0);
    stepSlider.value = "0";
    stepSlider.disabled = parsed.steps.length === 0;
    if (editorInput) editorInput.value = parsed.source;
    const noun = parsed.steps.length === 1 ? "step" : "steps";
    let suffix = parsed.timedOut ? " · stopped after the time limit" : parsed.truncated ? " · reached the step limit" : " · trace ready";
    setMessage(parsed.steps.length ? parsed.steps.length + " execution " + noun + suffix : "Trace loaded · no execution steps");
    if (editorMessage) {
      editorMessage.className = "editor-message success";
      editorMessage.textContent = parsed.steps.length ? "Trace ready · use Next to step through the program" : "This program produced no visible execution steps";
    }
    render();
  }

  function drawSource(source) {
    codeView.replaceChildren();
    source.split("\n").forEach((line, i) => {
      const row = document.createElement("div");
      row.className = "code-line";
      row.id = "line-" + (i + 1);
      const number = document.createElement("span");
      number.className = "lineno";
      number.textContent = String(i + 1);
      number.setAttribute("aria-hidden", "true");
      row.append(number, document.createTextNode(line));
      codeView.appendChild(row);
    });
  }

  function render() {
    if (!trace) return;
    const step = trace.steps[currentIndex];
    const hasSteps = Boolean(step);
    codeView.querySelectorAll(".code-line.current, .code-line.next").forEach((el) => {
      el.classList.remove("current", "next");
    });

    if (!hasSteps) {
      functionsPanel.textContent = "This trace has no recorded execution steps.";
      objectsPanel.textContent = "No heap snapshot is available.";
      outputPanel.textContent = trace.finalOutput || "(no output)";
      frameCount.textContent = "0";
      objectCount.textContent = "0";
      stepStatus.textContent = "No execution steps";
      updateButtons(false);
      return;
    }

    const currentLine = codeView.querySelector("#line-" + Number(step.line));
    if (currentLine) {
      currentLine.classList.add("current");
      currentLine.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
    const nextStep = trace.steps[currentIndex + 1];
    if (nextStep) {
      const nextLine = codeView.querySelector("#line-" + Number(nextStep.line));
      if (nextLine) nextLine.classList.add("next");
    }

    renderFrames(step.frames || []);
    renderObjects(step.heap || {});
    outputPanel.textContent = step.stdout || "";
    stepStatus.textContent = "Step " + (currentIndex + 1) + " of " + trace.steps.length + " · line " + step.line;
    stepSlider.value = String(currentIndex);
    updateButtons(true);
  }

  function updateButtons(hasSteps) {
    buttons.start.disabled = !hasSteps || currentIndex === 0;
    buttons.prev.disabled = !hasSteps || currentIndex === 0;
    buttons.next.disabled = !hasSteps || currentIndex >= trace.steps.length - 1;
    buttons.end.disabled = !hasSteps || currentIndex >= trace.steps.length - 1;
  }

  function renderFrames(frames) {
    frameCount.textContent = String(frames.length);
    functionsPanel.replaceChildren();
    if (!frames.length) {
      functionsPanel.className = "panel-content empty-state";
      functionsPanel.textContent = "No application frames were recorded at this step.";
      return;
    }
    functionsPanel.className = "panel-content";
    frames.forEach((frame, index) => {
      const box = document.createElement("div");
      box.className = "frame-box";
      const title = document.createElement("div");
      title.className = "frame-title";
      title.textContent = (index === 0 ? "▸ " : "") + (frame.method || "method") + "  ·  line " + (frame.line ?? "—");
      const locals = document.createElement("div");
      locals.className = "frame-locals";
      (frame.locals || []).forEach((local) => {
        const chip = document.createElement("span");
        chip.className = "local-chip";
        const name = document.createElement("span");
        name.className = "name";
        name.textContent = local.name || "value";
        chip.append(name, document.createTextNode(formatValueText(local.value)));
        locals.appendChild(chip);
      });
      if (!locals.childElementCount) {
        const noLocals = document.createElement("span");
        noLocals.className = "empty-state";
        noLocals.textContent = "No visible local variables";
        locals.appendChild(noLocals);
      }
      box.append(title, locals);
      functionsPanel.appendChild(box);
    });
  }

  function formatValueText(value) {
    if (value === null || value === undefined) return "null";
    if (typeof value === "object" && Object.hasOwn(value, "ref")) return " → id" + value.ref;
    if (typeof value === "string") return " " + JSON.stringify(value);
    return " " + String(value);
  }

  function renderObjects(heap) {
    const ids = Object.keys(heap);
    objectCount.textContent = String(ids.length);
    objectsPanel.replaceChildren();
    if (!ids.length) {
      objectsPanel.className = "panel-content empty-state";
      objectsPanel.textContent = "No heap objects are reachable at this step.";
      return;
    }
    objectsPanel.className = "panel-content";
    ids.forEach((id) => {
      const obj = heap[id] || {};
      const box = document.createElement("div");
      box.className = "object-box";
      const header = document.createElement("div");
      header.className = "obj-header";
      header.textContent = "id" + id + " · " + shortType(obj.type);
      const body = document.createElement("div");
      body.className = "obj-body";
      if (obj.kind === "array") {
        body.textContent = "[ " + (obj.elements || []).map(formatValueText).join(", ").trim() + " ]";
      } else if (obj.fields && typeof obj.fields === "object") {
        Object.entries(obj.fields).forEach(([key, value]) => {
          const field = document.createElement("div");
          field.className = "object-field";
          const label = document.createElement("span");
          label.className = "name";
          label.textContent = key;
          field.append(label, document.createTextNode("= " + formatValueText(value).trim()));
          body.appendChild(field);
        });
      } else {
        body.textContent = obj.summary || "Object details unavailable";
      }
      box.append(header, body);
      objectsPanel.appendChild(box);
    });
  }

  function shortType(type) {
    if (typeof type !== "string") return "object";
    const idx = type.lastIndexOf(".");
    return idx >= 0 ? type.slice(idx + 1) : type;
  }

  async function readTraceFile(file) {
    if (!file) return;
    try {
      const data = JSON.parse(await file.text());
      loadTrace(data, file.name);
    } catch (error) {
      showError(error instanceof SyntaxError ? "Couldn't read that JSON file. Choose a valid trace." : error.message);
    }
  }

  traceFileInput.addEventListener("change", (event) => {
    readTraceFile(event.target.files[0]);
    event.target.value = "";
  });

  buttons.start.addEventListener("click", () => { currentIndex = 0; render(); });
  buttons.end.addEventListener("click", () => { currentIndex = trace.steps.length - 1; render(); });
  buttons.prev.addEventListener("click", () => { if (currentIndex > 0) currentIndex--; render(); });
  buttons.next.addEventListener("click", () => { if (trace && currentIndex < trace.steps.length - 1) currentIndex++; render(); });
  stepSlider.addEventListener("input", () => {
    currentIndex = Number.parseInt(stepSlider.value, 10) || 0;
    render();
  });

  document.addEventListener("keydown", (event) => {
    if (!trace || event.altKey || event.ctrlKey || event.metaKey) return;
    if (event.target instanceof HTMLElement && /INPUT|TEXTAREA|SELECT|BUTTON/.test(event.target.tagName)) return;
    if (event.key === "ArrowRight") { event.preventDefault(); buttons.next.click(); }
    if (event.key === "ArrowLeft") { event.preventDefault(); buttons.prev.click(); }
    if (event.key === "Home") { event.preventDefault(); buttons.start.click(); }
    if (event.key === "End") { event.preventDefault(); buttons.end.click(); }
  });

  const workspace = document.querySelector(".workspace");
  let dragDepth = 0;
  workspace.addEventListener("dragenter", (event) => {
    event.preventDefault();
    dragDepth++;
    workspace.classList.add("dragging");
  });
  workspace.addEventListener("dragover", (event) => event.preventDefault());
  workspace.addEventListener("dragleave", (event) => {
    event.preventDefault();
    dragDepth = Math.max(0, dragDepth - 1);
    if (!dragDepth) workspace.classList.remove("dragging");
  });
  workspace.addEventListener("drop", (event) => {
    event.preventDefault();
    dragDepth = 0;
    workspace.classList.remove("dragging");
    const file = [...(event.dataTransfer?.files || [])].find((item) => item.name.toLowerCase().endsWith(".json"));
    if (file) readTraceFile(file);
    else showError("Drop a trace JSON file to open it.");
  });

  function setRunnerStatus(connected, message) {
    runnerStatus.dataset.state = connected ? "connected" : "disconnected";
    runnerStatusText.textContent = message;
    connectRunnerButton.textContent = connected ? "Runner connected" : "Connect local runner";
  }

  async function connectToLocalRunner() {
    connectRunnerButton.disabled = true;
    connectRunnerButton.textContent = "Connecting…";
    setRunnerStatus(false, "Checking local runner…");
    try {
      const response = await fetch(runnerBase + "/api/status", { method: "GET", mode: "cors" });
      const status = await response.json().catch(() => null);
      if (!response.ok || !status || status.ready !== true) {
        throw new Error(status && status.error ? status.error : "Local runner did not respond.");
      }
      runnerConnected = true;
      setRunnerStatus(true, "Local runner connected");
      editorMessage.className = "editor-message success";
      editorMessage.textContent = "Connected to the local JDK · your code will run on this computer";
      return true;
    } catch (error) {
      runnerConnected = false;
      setRunnerStatus(false, "Local runner not connected");
      editorMessage.className = "editor-message error";
      editorMessage.textContent = "Could not reach the runner. Start backend/run-server.bat and allow this site to access your local network if Chrome asks. You can also open http://127.0.0.1:8765 directly.";
      return false;
    } finally {
      connectRunnerButton.disabled = false;
    }
  }

  async function runProgram() {
    const source = editorInput.value;
    runButton.disabled = true;
    runButtonLabel.textContent = "Running on this computer…";
    editorMessage.className = "editor-message";
    editorMessage.textContent = "Compiling and tracing with the local JDK…";
    setMessage("Running Java with the local JDK…");

    try {
      if (!runnerConnected && !(await connectToLocalRunner())) throw new Error(editorMessage.textContent);
      const headers = { "Content-Type": "text/plain; charset=utf-8" };
      const input = stdinInput.value;
      if (input.length) {
        const bytes = new TextEncoder().encode(input);
        let binary = "";
        bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
        headers["X-Program-Input"] = btoa(binary);
      }
      const response = await fetch(runnerBase + "/api/trace", { method: "POST", headers, body: source, mode: "cors" });
      const result = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(result && result.error ? result.error : "The local runner is not connected. Start it, then open http://127.0.0.1:8765.");
      }
      loadTrace(result, "Main.java");
      editorMessage.textContent = "Done · " + trace.steps.length + " steps recorded by the JVM";
      document.querySelector(".workspace").scrollIntoView({ block: "start", behavior: "smooth" });
    } catch (error) {
      const message = error instanceof TypeError
        ? "Could not reach the local runner. Start backend/run-server.bat and keep its window open. If Chrome asks, allow this site to access your local network; or open http://127.0.0.1:8765 directly."
        : error.message;
      runnerConnected = false;
      setRunnerStatus(false, "Local runner not connected");
      editorMessage.className = "editor-message error";
      editorMessage.textContent = message;
      showError(message);
    } finally {
      runButton.disabled = false;
      runButtonLabel.textContent = "Run & visualize";
    }
  }

  runButton.addEventListener("click", runProgram);
  connectRunnerButton.addEventListener("click", connectToLocalRunner);
  editorInput.addEventListener("input", () => {
    editorMessage.className = "editor-message";
    editorMessage.textContent = "Ready · run to create a fresh execution trace";
  });
  editorInput.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
      event.preventDefault();
      runButton.click();
    }
    if (event.key === "Tab") {
      event.preventDefault();
      const start = editorInput.selectionStart;
      const end = editorInput.selectionEnd;
      editorInput.setRangeText("    ", start, end, "end");
    }
  });
  loadExampleButton.addEventListener("click", async () => {
    try {
      if (!demoTrace) {
        const response = await fetch("sample-trace.json");
        if (!response.ok) throw new Error("Example trace is not available.");
        demoTrace = await response.json();
      }
      loadTrace(demoTrace, "Sample.java");
    } catch (error) {
      editorMessage.className = "editor-message error";
      editorMessage.textContent = error.message;
    }
  });

  fetch("sample-trace.json")
    .then((response) => {
      if (!response.ok) throw new Error("Example trace unavailable");
      return response.json();
    })
    .then((data) => {
      demoTrace = data;
      loadTrace(data, "Sample.java");
    })
    .catch(() => {
      setMessage("Run code with the local runner, or open a trace JSON");
      stepStatus.textContent = "No trace loaded";
      outputPanel.textContent = "(run your code to see output)";
      editorMessage.textContent = "Start the local runner to execute this program.";
    });
})();