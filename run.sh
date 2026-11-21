#!/usr/bin/env bash
set -euo pipefail
PORT="${1:-8080}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
printf 'MONNIXCODE HTML is running at http://localhost:%s\n' "$PORT"
printf 'Press Ctrl+C to stop.\n'
cd "$ROOT"
exec python3 -m http.server "$PORT"
