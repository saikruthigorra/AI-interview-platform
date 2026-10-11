#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# InterviewIQ doctor — prints a diagnostic report you can paste back for help.
#
#   Run:  bash scripts/doctor.sh        (Git Bash on Windows, or macOS/Linux)
# ─────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

line() { printf '%s\n' "──────────────────────────────────────────────"; }

echo "InterviewIQ doctor"
line
echo "project folder : $ROOT"
echo "date           : $(date)"
line

echo "1. Files"
for f in .env.local .env.example README.md backend/pom.xml frontend/package.json \
         scripts/dev-backend.sh scripts/run-backend.bat; do
  if [ -e "$f" ]; then echo "   ok        $f"; else echo "   MISSING   $f"; fi
done
line

echo "2. Tools on PATH"
if command -v java >/dev/null 2>&1; then echo "   java   : $(java -version 2>&1 | head -1)"; else echo "   java   : NOT FOUND  (install JDK 17+)"; fi
if command -v mvn  >/dev/null 2>&1; then echo "   maven  : $(mvn -v 2>/dev/null | head -1)"; else echo "   maven  : NOT FOUND  (install Maven 3.8+)"; fi
if command -v node >/dev/null 2>&1; then echo "   node   : $(node -v)"; else echo "   node   : NOT FOUND  (install Node 18+)"; fi
if command -v npm  >/dev/null 2>&1; then echo "   npm    : $(npm -v)"; else echo "   npm    : NOT FOUND"; fi
line

echo "3. .env.local"
if [ ! -f .env.local ]; then
  echo "   MISSING — copy .env.example to .env.local and fill in your values."
else
  if grep -q $'\r' .env.local 2>/dev/null; then
    echo "   WARNING: Windows (CRLF) line endings detected. Bash would load values with a"
    echo "            stray character. Fix with:  sed -i 's/\r$//' .env.local"
  else
    echo "   line endings: unix (good)"
  fi
  for key in SPRING_PROFILES_ACTIVE APP_SEED TIDB_HOST TIDB_PORT TIDB_DATABASE TIDB_USER TIDB_SSL_MODE; do
    val="$(grep -m1 "^${key}=" .env.local | cut -d= -f2- | tr -d '\r')"
    if [ -n "$val" ]; then echo "   $key=$val"; else echo "   $key=<empty>"; fi
  done
  if grep -qE '^TIDB_PASSWORD=.+' .env.local; then echo "   TIDB_PASSWORD=***set***"; else echo "   TIDB_PASSWORD=<empty>"; fi
fi
line

echo "4. Network"
HOSTV="$(grep -m1 '^TIDB_HOST=' .env.local 2>/dev/null | cut -d= -f2- | tr -d '\r')"
PORTV="$(grep -m1 '^TIDB_PORT=' .env.local 2>/dev/null | cut -d= -f2- | tr -d '\r')"
PORTV="${PORTV:-4000}"
if [ -n "$HOSTV" ]; then
  if (exec 3<>"/dev/tcp/$HOSTV/$PORTV") 2>/dev/null; then
    echo "   TCP $HOSTV:$PORTV  reachable ✓"
  elif command -v nc >/dev/null 2>&1 && nc -z -w 5 "$HOSTV" "$PORTV" >/dev/null 2>&1; then
    echo "   TCP $HOSTV:$PORTV  reachable ✓ (via nc)"
  else
    echo "   TCP $HOSTV:$PORTV  NOT reachable ✗  (typo? VPN/proxy? cluster sleeping?)"
  fi
else
  echo "   TIDB_HOST empty — skipping TiDB reachability test"
fi
if command -v curl >/dev/null 2>&1; then
  curl -s -o /dev/null --max-time 6 http://localhost:8080/actuator/health \
    && echo "   localhost:8080  backend is RUNNING ✓" || echo "   localhost:8080  backend not running (fine if you haven't started it)"
  curl -s -o /dev/null --max-time 6 http://localhost:5173/ \
    && echo "   localhost:5173  frontend is RUNNING ✓" || echo "   localhost:5173  frontend not running (fine if you haven't started it)"
fi
line
echo "Next:  scripts/dev-backend.sh   (or double-click scripts\\run-backend.bat)"
line
