@echo off
setlocal
cd /d "%~dp0"
call mvn -q clean package
if errorlevel 1 (
  echo Build failed.
  pause
  exit /b 1
)
echo Built target\focusflow.jar
endlocal
