@echo off
setlocal
cd /d "%~dp0"

rem FocusFlow keeps running in the background (widget + tray), which locks target\focusflow.jar.
rem Close it first so the build can replace the file.
taskkill /f /im FocusFlow.exe >nul 2>&1
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe'\" | Where-Object { $_.CommandLine -like '*focusflow.jar*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
timeout /t 1 /nobreak >nul

call mvn -q clean package
if errorlevel 1 (
  echo Build failed.
  pause
  exit /b 1
)
echo Built target\focusflow.jar
endlocal
