#!/usr/bin/env bash
# Starts the original console interface (in-memory demo, no database).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [ ! -d "$ROOT/out" ]; then
  "$ROOT/build.sh"
fi

exec java -cp "out:lib/*" Main
