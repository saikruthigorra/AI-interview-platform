@echo off
rem ============================================================================
rem  Build ONE deployable artefact (Windows version of scripts/build-app.sh):
rem    frontend (React) -> backend/src/main/resources/static -> backend\target\*.jar
rem  The jar serves the website AND the API on a single port.
rem ============================================================================
setlocal
pushd "%~dp0.."
echo ==========================================================
echo   InterviewIQ - build the deployable jar
echo ==========================================================
echo Folder: %CD%
echo.

where node >nul 2>nul || (echo [X] Node.js not found on PATH. Install Node 18+ and retry. & pause & popd & exit /b 1)
where mvn  >nul 2>nul || (echo [X] Maven not found on PATH. Install Maven and retry.    & pause & popd & exit /b 1)

echo [1/3] Building the frontend...
cd frontend
if not exist node_modules (
  echo       first run: npm install...
  call npm install || (echo [X] npm install failed. & pause & popd & exit /b 1)
)
call npm run build || (echo [X] frontend build failed. & pause & popd & exit /b 1)
cd ..

echo [2/3] Copying the website into the backend...
if exist backend\src\main\resources\static rmdir /s /q backend\src\main\resources\static
mkdir backend\src\main\resources\static
xcopy /e /i /y /q frontend\dist backend\src\main\resources\static >nul

echo [3/3] Packaging the application jar...
cd backend
call mvn -q -B -DskipTests package || (echo [X] Maven package failed. & pause & popd & exit /b 1)
cd ..

echo.
for %%f in (backend\target\interviewiq-api-*.jar) do echo Done: %%f
echo.
echo Run it with:  java -jar backend\target\interviewiq-api-0.1.0.jar
pause
popd
endlocal
