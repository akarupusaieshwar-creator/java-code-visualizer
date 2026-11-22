#!/usr/bin/env bash
# Start the local Java Visualizer at http://127.0.0.1:8765
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
PORT="${1:-8765}"
mkdir -p out
javac --add-modules jdk.jdi,jdk.httpserver -d out src/main/java/visualizer/*.java
java --add-modules jdk.jdi,jdk.httpserver -cp out visualizer.LocalServer "$SCRIPT_DIR/../frontend" "$PORT"
