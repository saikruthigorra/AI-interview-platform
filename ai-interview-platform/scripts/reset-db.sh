#!/usr/bin/env bash
# Wipes the zero-setup local database so the next boot re-seeds demo data.
set -e
TARGET="${LOCAL_DB_PATH:-/tmp/interviewiq}"
rm -f "$TARGET".mv.db "$TARGET".trace.db
echo "Removed ${TARGET}.mv.db — restart the backend to re-seed."
