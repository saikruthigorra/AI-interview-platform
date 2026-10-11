#!/usr/bin/env bash
# Optional: spin up a local TiDB cluster with TiUP (dev only) and point the API at it.
#   1. install tiup:  curl --proto '=https' --tlsv1.2 -sSf https://tiup-mirrors.pingcap.com/install.sh | sh
#   2. run this script, then in another shell: scripts/dev-backend.sh
set -e
echo "Starting a local TiDB playground on ports 4000/10080…"
tiup playground --tag interviewiq
# TiDB listens on 127.0.0.1:4000 (MySQL protocol). Then run the backend with:
#   SPRING_PROFILES_ACTIVE=tidb TIDB_HOST=127.0.0.1 TIDB_PORT=4000 \
#   TIDB_USER=root TIDB_PASSWORD= TIDB_DATABASE=interviewiq TIDB_SSL_MODE=DISABLED \
#   scripts/dev-backend.sh
# and create the schema once:  mysql -h 127.0.0.1 -P 4000 -u root -e "CREATE DATABASE IF NOT EXISTS interviewiq;"
