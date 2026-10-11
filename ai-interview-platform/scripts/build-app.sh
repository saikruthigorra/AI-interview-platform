#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Build ONE deployable artefact:
#   frontend (React) → backend/src/main/resources/static/ → backend/target/interviewiq-api-0.1.0.jar
#
# The resulting jar is a self-contained app: it serves the website AND the API
# on one port, so any host that runs a jar (Render, Fly.io, Railway, Cloud Run,
# a VPS…) works without a second service or CORS setup.
#
#   Usage:  scripts/build-app.sh          # requires JDK 17+ , Maven, Node 18+
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "▸ 1/3  building the frontend"
cd frontend
[ -d node_modules ] || npm install
npm run build
cd "$ROOT"

echo "▸ 2/3  copying the website into the backend"
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -r frontend/dist/. backend/src/main/resources/static/

echo "▸ 3/3  packaging the application jar"
cd backend
mvn -q -B -DskipTests package
cd "$ROOT"

JAR="$(ls backend/target/interviewiq-api-*.jar | head -1)"
echo
echo "✔ done: $JAR  ($(du -h "$JAR" | cut -f1))"
echo
echo "Run it locally:   java -jar $JAR"
echo "  (reads .env.local only via scripts/dev-backend.sh — for a bare 'java -jar'"
echo "   export the variables first, or run with no config for the local file DB)"
