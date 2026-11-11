#!/usr/bin/env bash
# Starts the web API on http://localhost:8080 (override with PORT, database path with DB_PATH).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [ ! -d "$ROOT/out" ]; then
  "$ROOT/build.sh"
fi

PORT_TO_USE="${1:-${PORT:-8080}}"
# Fall back to 8080 for anything that is not a usable port number, so a stray
# shell comment or a PORT like "0" does not break the start-up.
case "$PORT_TO_USE" in
  '' | 0 | *[!0-9]*) PORT_TO_USE=8080 ;;
esac

exec java -cp "out:lib/*" api.ApiServer "$PORT_TO_USE"
