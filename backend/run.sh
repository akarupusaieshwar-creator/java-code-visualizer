#!/usr/bin/env bash
# Usage: ./run.sh <path-to-target.java> [stdin-file] [max-steps]
# Compiles the tracer (if needed), traces the target program, and writes
# the result straight into ../frontend/trace.json so the UI can load it.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

TARGET="${1:?Usage: ./run.sh <path-to-target.java> [stdin-file] [max-steps]}"
STDIN_FILE="${2:-}"
MAX_STEPS="${3:-400}"
OUT_JSON="../frontend/trace.json"

if [ ! -d out ] || [ src/main/java/visualizer/Tracer.java -nt out/visualizer/Tracer.class ]; then
  echo "Compiling tracer..."
  mkdir -p out
  javac -d out src/main/java/visualizer/Tracer.java src/main/java/visualizer/Json.java
fi

if [ -n "$STDIN_FILE" ]; then
  java -cp out visualizer.Tracer "$TARGET" "$OUT_JSON" "$STDIN_FILE" "$MAX_STEPS"
else
  java -cp out visualizer.Tracer "$TARGET" "$OUT_JSON" "" "$MAX_STEPS"
fi

echo "Done. Open frontend/index.html and load frontend/trace.json (or just refresh if your browser auto-loads it)."
