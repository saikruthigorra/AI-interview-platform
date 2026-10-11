@echo off
rem ============================================================================
rem  InterviewIQ - start the API (backend).
rem  Double-click this file. It loads .env.local, checks your tools, then runs
rem  Spring Boot on http://localhost:8080. Leave the window open; Ctrl+C stops.
rem ============================================================================
setlocal enabledelayedexpansion
pushd "%~dp0.."
echo ==========================================================
echo   InterviewIQ - backend (API)
echo ==========================================================
echo Folder: %CD%
echo.

if not exist ".env.local" (
  echo [X] .env.local was not found in:
  echo     %CD%
  echo     This file holds your config. Copy .env.example to .env.local
  echo     and fill in the TiDB values, then run this again.
  echo.
  pause
  popd
  exit /b 1
)

rem --- load KEY=VALUE lines from .env.local (skip blanks and # comments) ---
for /f "usebackq tokens=1,* delims==" %%a in (".env.local") do (
  set "k=%%a"
  if not "!k!"=="" if not "!k:~0,1!"=="#" set "!k!=%%b"
)

echo Config loaded from .env.local:
echo   profile : !SPRING_PROFILES_ACTIVE!
echo   TiDB    : !TIDB_USER! @ !TIDB_HOST!:!TIDB_PORT!/!TIDB_DATABASE!
if "!SPRING_PROFILES_ACTIVE!"=="" echo   (profile empty = using the local file database, no TiDB)
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo [X] Java not found on PATH. Install JDK 17 or newer ^(adoptium.net^), then re-open this window.
  echo.
  pause
  popd
  exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
  echo [X] Maven not found on PATH. Install Maven ^(maven.apache.org^) and add its bin folder to PATH.
  echo.
  pause
  popd
  exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do echo Java %%v

echo.
echo Starting the API on http://localhost:8080  -  first run downloads jars, be patient.
echo Leave this window OPEN. Press Ctrl+C to stop the server.
echo ----------------------------------------------------------
cd backend
call mvn spring-boot:run
echo.
echo ----------------------------------------------------------
echo The API has stopped.
pause
popd
endlocal
