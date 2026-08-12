#!/usr/bin/env bash
#
# Exports the live OpenAPI spec to openapi.json in the repo root.
#
# Requires: Java 21, Docker Desktop, and a populated .env (see .env.prod.example
# for the variable shape). Run from the repo root:
#
#   ./scripts/export-openapi.sh
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT="${SERVER_PORT:-8080}"
OUT_FILE="${1:-openapi.json}"

echo "==> Starting local infrastructure (mysql, kafka, kafka-init, redis)..."
docker compose -f docker-compose.prod.yml up -d mysql kafka kafka-init redis

echo "==> Waiting for infrastructure to settle..."
sleep 10

echo "==> Starting the app in the background (dev profile)..."
./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev >/tmp/afrochow-openapi-export.log 2>&1 &
APP_PID=$!

cleanup() {
  echo "==> Stopping app (pid $APP_PID)..."
  kill "$APP_PID" >/dev/null 2>&1 || true
  wait "$APP_PID" 2>/dev/null || true
}
trap cleanup EXIT

echo "==> Waiting for the app to become healthy on port $PORT..."
for i in $(seq 1 60); do
  if curl -sf "http://localhost:${PORT}/api/actuator/health" >/dev/null 2>&1; then
    echo "==> App is up."
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "App did not become healthy in time. See /tmp/afrochow-openapi-export.log" >&2
    exit 1
  fi
  sleep 2
done

echo "==> Pulling OpenAPI spec from /api/v3/api-docs..."
curl -sf "http://localhost:${PORT}/api/v3/api-docs" -o "$OUT_FILE"

echo "==> Wrote $OUT_FILE"
