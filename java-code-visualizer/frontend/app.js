(() => {
  const codeView = document.getElementById("codeView");
  const functionsPanel = document.getElementById("functionsPanel");
  const objectsPanel = document.getElementById("objectsPanel");
  const outputPanel = document.getElementById("outputPanel");
  const stepStatus = document.getElementById("stepStatus");
  const stepSlider = document.getElementById("stepSlider");
  const classNameLabel = document.getElementById("classNameLabel");

  const btnStart = document.getElementById("btnStart");
  const btnPrev = document.getElementById("btnPrev");
  const btnNext = document.getElementById("btnNext");
  const btnEnd = document.getElementById("btnEnd");
  const traceFileInput = document.getElementById("traceFileInput");

  let trace = null;
  let sourceLines = [];
  let currentIndex = 0;

  traceFileInput.addEventListener("change", (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        loadTrace(JSON.parse(reader.result));
      } catch (err) {
        alert("Could not parse trace.json: " + err.message);
      }
    };
    reader.readAsText(file);
  });

  function loadTrace(data) {
    trace = data;
    sourceLines = trace.source.split("\n");
    currentIndex = 0;
    classNameLabel.textContent = trace.className || "your code";
    renderCodeSkeleton();
    stepSlider.max = Math.max(trace.steps.length - 1, 0);
    stepSlider.value = 0;
    render();
  }

  function renderCodeSkeleton() {
    codeView.innerHTML = "";
    sourceLines.forEach((line, i) => {
      const div = document.createElement("div");
      div.className = "code-line";
      div.id = "line-" + (i + 1);
      div.innerHTML =
        '<span class="lineno">' + (i + 1) + "</span>" + escapeHtml(line);
      codeView.appendChild(div);
    });
  }

  function escapeHtml(s) {
    return s
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function render() {
    if (!trace || trace.steps.length === 0) return;
    const step = trace.steps[currentIndex];
    const nextStep = trace.steps[currentIndex + 1];

    // Highlight current / next line
    document.querySelectorAll(".code-line").forEach((el) => {
      el.classList.remove("current", "next");
    });
    const curEl = document.getElementById("line-" + step.line);
    if (curEl) {
      curEl.classList.add("current");
      curEl.scrollIntoView({ block: "center", behavior: "smooth" });
    }
    if (nextStep) {
      const nextEl = document.getElementById("line-" + nextStep.line);
      if (nextEl) nextEl.classList.add("next");
    }

    renderFunctions(step);
    renderObjects(step);
    outputPanel.textContent = step.stdout || "";

    stepStatus.textContent = `Step ${currentIndex + 1} of ${trace.steps.length}`;
    stepSlider.value = currentIndex;

    btnStart.disabled = currentIndex === 0;
    btnPrev.disabled = currentIndex === 0;
    btnNext.disabled = currentIndex === trace.steps.length - 1;
    btnEnd.disabled = currentIndex === trace.steps.length - 1;
  }

  function renderFunctions(step) {
    functionsPanel.innerHTML = "";
    // Deepest frame first (top of call stack), like a real debugger.
    step.frames.forEach((frame) => {
      const box = document.createElement("div");
      box.className = "frame-box";

      const title = document.createElement("div");
      title.className = "frame-title";
      title.textContent = frame.method + "  (line " + frame.line + ")";
      box.appendChild(title);

      const locals = document.createElement("div");
      locals.className = "frame-locals";
      frame.locals.forEach((local) => {
        const chip = document.createElement("span");
        chip.className = "local-chip";
        chip.innerHTML =
          '<span class="name">' +
          escapeHtml(local.name) +
          "</span>" +
          formatValue(local.value);
        locals.appendChild(chip);
      });
      box.appendChild(locals);

      functionsPanel.appendChild(box);
    });
  }

  function formatValue(v) {
    if (v === null || v === undefined) return '<span class="ref">null</span>';
    if (typeof v === "object" && "ref" in v) {
      return '<span class="ref">id' + v.ref + "</span>";
    }
    if (typeof v === "string") return escapeHtml(JSON.stringify(v));
    return escapeHtml(String(v));
  }

  function renderObjects(step) {
    objectsPanel.innerHTML = "";
    const heap = step.heap || {};
    const ids = Object.keys(heap);
    if (ids.length === 0) {
      objectsPanel.innerHTML =
        '<div style="color:var(--text-dim); font-size:12px;">(none yet)</div>';
      return;
    }
    ids.forEach((id) => {
      const obj = heap[id];
      const box = document.createElement("div");
      box.className = "object-box";

      const header = document.createElement("div");
      header.className = "obj-header";
      header.textContent = "id" + id + " : " + shortType(obj.type);
      box.appendChild(header);

      const body = document.createElement("div");
      body.className = "obj-body";
      if (obj.kind === "array") {
        body.innerHTML =
          "[ " +
          obj.elements.map((e) => formatValue(e)).join(", ") +
          " ]";
      } else if (obj.fields) {
        body.innerHTML = Object.entries(obj.fields)
          .map(
            ([k, v]) =>
              '<div><span class="name" style="color:var(--text-dim)">' +
              escapeHtml(k) +
              "</span> = " +
              formatValue(v) +
              "</div>"
          )
          .join("");
      } else if (obj.summary !== undefined) {
        body.textContent = obj.summary;
      }
      box.appendChild(body);

      objectsPanel.appendChild(box);
    });
  }

  function shortType(t) {
    if (!t) return "";
    const idx = t.lastIndexOf(".");
    return idx >= 0 ? t.substring(idx + 1) : t;
  }

  btnStart.addEventListener("click", () => {
    currentIndex = 0;
    render();
  });
  btnEnd.addEventListener("click", () => {
    currentIndex = trace.steps.length - 1;
    render();
  });
  btnPrev.addEventListener("click", () => {
    if (currentIndex > 0) currentIndex--;
    render();
  });
  btnNext.addEventListener("click", () => {
    if (trace && currentIndex < trace.steps.length - 1) currentIndex++;
    render();
  });
  stepSlider.addEventListener("input", () => {
    currentIndex = parseInt(stepSlider.value, 10);
    render();
  });

  document.addEventListener("keydown", (e) => {
    if (!trace) return;
    if (e.key === "ArrowRight") btnNext.click();
    if (e.key === "ArrowLeft") btnPrev.click();
  });

  // Best-effort auto-load of ./trace.json when served over http(s).
  // (Silently does nothing under file:// or if the file isn't there yet.)
  if (location.protocol !== "file:") {
    fetch("trace.json")
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then(loadTrace)
      .catch(() => {});
  }
})();
