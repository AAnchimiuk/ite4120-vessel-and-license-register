#!/usr/bin/env bash
# Start the frontend dev server (Vite, port 18640).
# On a fresh clone the dependencies are installed first — without them the
# failure is the cryptic `sh: vite: command not found`.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/frontend"

if [ ! -x node_modules/.bin/vite ]; then
  echo "frontend dependencies are not installed yet — running npm ci (first time only)..."
  echo "(a 401 here means your GitHub token is missing: README § GitHub token)"
  npm ci
fi

exec npm run dev
