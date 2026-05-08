#!/usr/bin/env bash
#
# Start WireMock standalone on port 8089 with the stubs in wiremock-stubs/.
# WireMock impersonates the external Payment Processor for local development.
#
# Usage:   ./scripts/start-wiremock.sh
# Stop:    Ctrl-C
#
# The first run downloads the WireMock JAR (~25 MB) into scripts/.cache/.
# Subsequent runs reuse it.

set -euo pipefail

WIREMOCK_VERSION="3.6.0"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCAFFOLD_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CACHE_DIR="$SCRIPT_DIR/.cache"
JAR="$CACHE_DIR/wiremock-standalone-${WIREMOCK_VERSION}.jar"
URL="https://repo1.maven.org/maven2/org/wiremock/wiremock-standalone/${WIREMOCK_VERSION}/wiremock-standalone-${WIREMOCK_VERSION}.jar"

mkdir -p "$CACHE_DIR"

if [ ! -f "$JAR" ]; then
  echo "Downloading WireMock $WIREMOCK_VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --progress-bar -o "$JAR" "$URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q --show-progress -O "$JAR" "$URL"
  else
    echo "Need either curl or wget to download WireMock" >&2
    exit 1
  fi
fi

cd "$SCAFFOLD_DIR"
echo "Starting WireMock on http://localhost:8089"
echo "Stubs from: $SCAFFOLD_DIR/wiremock-stubs/mappings"
echo
exec java -jar "$JAR" \
  --port 8089 \
  --root-dir wiremock-stubs \
  --global-response-templating \
  --verbose
