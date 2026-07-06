#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

PORT=8080
PID=$(netstat -ano | grep "LISTENING" | grep ":$PORT " | awk '{print $5}' | head -1 || true)

if [ -n "${PID:-}" ]; then
  echo "Stopping app (PID $PID) on port $PORT..."
  taskkill //PID "$PID" //F > /dev/null 2>&1 || true
  echo "App stopped."
else
  echo "No app found listening on port $PORT."
fi

echo "Stopping Postgres..."
docker stop school-app-postgres > /dev/null 2>&1 || echo "Postgres container was not running."
echo "Postgres container stopped (data preserved). Run './start-local.sh' to start everything again."
