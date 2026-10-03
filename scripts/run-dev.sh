#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
MVN="${MVN:-mvn}"
command -v "$MVN" >/dev/null 2>&1 || { echo "Maven not found. Install Maven 3.9+ or use 'make up' (Docker)."; exit 1; }
echo "Starting on http://localhost:${SERVER_PORT:-8080} (5,000 demo accounts, ~100s per bill run)"
exec "$MVN" -B spring-boot:run
