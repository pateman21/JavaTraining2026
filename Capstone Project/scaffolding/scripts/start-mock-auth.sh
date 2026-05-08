#!/usr/bin/env bash
# Start the Mock Authorization Server (Spring Authorization Server) on port 9000.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../backend/mock-auth"

echo "Starting Mock Auth Server on http://localhost:9000 ..."
mvn spring-boot:run
