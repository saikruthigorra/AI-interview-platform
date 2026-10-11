@echo off
rem ============================================================================
rem  InterviewIQ - start the website (frontend).
rem  Double-click this file AFTER run-backend.bat is running. Opens on
rem  http://localhost:5173. Leave the window open; Ctrl+C stops.
rem ============================================================================
setlocal
pushd "%~dp0..\frontend"
echo ==========================================================
echo   InterviewIQ - website (UI)
echo ==========================================================
echo Folder: %CD%
echo.

if not exist "package.json" (
  echo [X] package.json not found - this file must stay inside the project's scripts\ folder.
  pause
  popd
  exit /b 1
)

where node >nul 2>nul
if errorlevel 1 (
  echo [X] Node.js not found on PATH. Install Node 18 or newer ^(nodejs.org^), then re-open this window.
  echo.
  pause
  popd
  exit /b 1
)
for /f "delims=" %%v in ('node -v') do echo Node %%v

if not exist "node_modules" (
  echo.
  echo First run: installing npm packages. This takes a minute or two...
  call npm install
  if errorlevel 1 (
    echo.
    echo [X] npm install failed - check your internet connection and try again.
    pause
    popd
    exit /b 1
  )
)

echo.
echo Starting the website on http://localhost:5173
echo Leave this window OPEN. Press Ctrl+C to stop.
echo (The backend window must stay open too.)
echo ----------------------------------------------------------
call npm run dev
echo.
echo ----------------------------------------------------------
echo The website has stopped.
pause
popd
endlocal
