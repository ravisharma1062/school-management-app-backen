#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "Starting Postgres..."
docker compose up -d

echo "Waiting for Postgres to be ready..."
until docker compose exec -T postgres pg_isready -U school_app -d school_app > /dev/null 2>&1; do
  sleep 1
done
echo "Postgres is ready."

echo "Starting the app (Ctrl+C stops the app; Postgres keeps running in Docker)..."
mvn spring-boot:run
