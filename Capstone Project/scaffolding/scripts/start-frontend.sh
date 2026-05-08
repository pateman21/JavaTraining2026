#!/usr/bin/env bash
#
# Install (if needed) and run the Vite dev server on port 5173.
#
# Usage:   ./scripts/start-frontend.sh
# Stop:    Ctrl-C
#
# Requires: Node 20+, npm. Reads VITE_* vars from frontend/.env.local.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$(cd "$SCRIPT_DIR/../frontend" && pwd)"

cd "$FRONTEND_DIR"

if [ ! -d node_modules ]; then
  echo "node_modules not found — running npm install (one-time)..."
  npm install --no-audit --no-fund
fi

if [ ! -f .env.local ]; then
  echo
  echo "Warning: $FRONTEND_DIR/.env.local does not exist."
  echo "Copy .env.example to .env.local and set VITE_GOOGLE_CLIENT_ID before signing in."
  echo
fi

exec npm run dev
