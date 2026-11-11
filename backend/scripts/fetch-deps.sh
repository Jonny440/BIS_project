#!/usr/bin/env bash
# Downloads the jars the backend needs into backend/lib. The jars are not committed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIB="$ROOT/lib"

SQLITE_JDBC_VERSION="3.54.1.0"
SLF4J_VERSION="2.0.17"
BASE="https://repo1.maven.org/maven2"

mkdir -p "$LIB"

fetch() {
  local url="$1"
  local target="$2"
  if [ -f "$target" ]; then
    return
  fi
  echo "Downloading $(basename "$target")..."
  curl -fsSL "$url" -o "$target"
}

fetch "$BASE/org/xerial/sqlite-jdbc/$SQLITE_JDBC_VERSION/sqlite-jdbc-$SQLITE_JDBC_VERSION.jar" "$LIB/sqlite-jdbc.jar"
fetch "$BASE/org/slf4j/slf4j-api/$SLF4J_VERSION/slf4j-api-$SLF4J_VERSION.jar" "$LIB/slf4j-api.jar"
fetch "$BASE/org/slf4j/slf4j-nop/$SLF4J_VERSION/slf4j-nop-$SLF4J_VERSION.jar" "$LIB/slf4j-nop.jar"

echo "Dependencies ready in $LIB"
