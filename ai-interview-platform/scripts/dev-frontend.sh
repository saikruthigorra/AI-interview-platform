#!/usr/bin/env bash
# Run the React dev server (proxies /api to http://localhost:8080).
set -e
cd "$(dirname "$0")/../frontend"
[ -d node_modules ] || npm install
exec npm run dev
