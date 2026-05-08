#!/usr/bin/env bash
#
# Convenience wrapper: load env vars from .env (if present) and run the
# Spring Boot service in the foreground via Maven.
#
# Usage:   ./scripts/start-backend.sh
# Stop:    Ctrl-C
#
# Requires: JDK 17, Maven 3.9+, Oracle reachable at $ORACLE_URL.
# See README.md for first-time setup.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCAFFOLD_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCAFFOLD_DIR/.env"

if [ -f "$ENV_FILE" ]; then
  echo "Loading env vars from $ENV_FILE"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
else
  echo "No .env found at $ENV_FILE — using shell environment only."
  echo "Copy .env.example to .env to customize."
fi

cd "$SCAFFOLD_DIR/backend"
exec mvn spring-boot:run
