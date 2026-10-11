#!/usr/bin/env bash
# Run the Spring Boot API. Needs JDK 17+ and Maven on PATH.
# Sources .env.local (or .env) from the project root when present, so secrets
# stay out of your shell history and out of git.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
for f in "$ROOT/.env.local" "$ROOT/.env"; do
  if [ -f "$f" ]; then
    echo "Loading $f"
    set -a
    # shellcheck disable=SC1090
    . "$f"
    set +a
    break
  fi
done
cd "$ROOT/backend"
exec mvn spring-boot:run
