@echo off
setlocal
cd /d "%~dp0"

if not exist "target\focusflow.jar" (
  echo Building FocusFlow first...
  call mvn -q package
  if errorlevel 1 goto fail
)
if not exist build mkdir build
java -cp target\classes com.focusflow.app.IconExporter build\focusflow.ico

set "JAVAW="
for /f "delims=" %%i in ('where javaw 2^>nul') do if not defined JAVAW set "JAVAW=%%i"
if not defined JAVAW goto nojava
set "APPDIR=%CD%"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$w=New-Object -ComObject WScript.Shell; foreach($d in 'Desktop','Programs'){ $s=$w.CreateShortcut([Environment]::GetFolderPath($d)+'\FocusFlow.lnk'); $s.TargetPath='%JAVAW%'; $s.Arguments='-Xms64m -Xmx384m -jar \"%APPDIR%\target\focusflow.jar\"'; $s.WorkingDirectory='%APPDIR%'; $s.IconLocation='%APPDIR%\build\focusflow.ico'; $s.Description='FocusFlow'; $s.Save() }"
if errorlevel 1 goto fail

echo.
echo Done. FocusFlow is now on your Desktop and in the Start menu.
echo Do not move or delete this folder, the shortcut points to it.
pause
exit /b 0

:nojava
echo Could not find javaw. Install JDK 21 and make sure it is on your PATH.
pause
exit /b 1

:fail
echo Something went wrong. Make sure JDK 21 and Maven are installed.
pause
exit /b 1
