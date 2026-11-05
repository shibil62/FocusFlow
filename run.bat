@echo off
setlocal
cd /d "%~dp0"
if not exist "target\focusflow.jar" (
  echo First run: building FocusFlow. This takes a minute...
  call mvn -q package
  if errorlevel 1 (
    echo.
    echo Build failed. Make sure JDK 21 and Maven are installed and on your PATH.
    pause
    exit /b 1
  )
)
start "" javaw -Xms64m -Xmx384m -jar "target\focusflow.jar"
endlocal
