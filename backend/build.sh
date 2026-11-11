#!/usr/bin/env bash
# Compiles the backend sources into backend/out. No Maven or Gradle required.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
"$ROOT/scripts/fetch-deps.sh"

SOURCES="$(mktemp)"
trap 'rm -f "$SOURCES"' EXIT

find "$ROOT/src" -name '*.java' -print > "$SOURCES"
rm -rf "$ROOT/out"
mkdir -p "$ROOT/out"

javac -encoding UTF-8 -Xlint:-options -d "$ROOT/out" -cp "$ROOT/lib/*" @"$SOURCES"
echo "Compiled $(wc -l < "$SOURCES" | tr -d ' ') source files into $ROOT/out"
