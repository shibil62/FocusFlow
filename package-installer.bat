@echo off
setlocal
cd /d "%~dp0"

echo [1/4] Building the jar...
call mvn -q clean package
if errorlevel 1 goto fail

echo [2/4] Generating the application icon...
if not exist build mkdir build
java -cp target\classes com.focusflow.app.IconExporter build\focusflow.ico
if errorlevel 1 goto fail

echo [3/4] Preparing the jpackage input folder...
if exist build\input rmdir /s /q build\input
mkdir build\input
copy /y target\focusflow.jar build\input\ >nul

echo [4/4] Running jpackage...
set MODS=java.desktop,java.sql,java.logging,java.naming,java.management,java.prefs,jdk.unsupported
rem The upgrade UUID must never change. It lets a newer installer replace an older install in place.
jpackage --type exe --name FocusFlow --app-version 1.0.0 --vendor "Shibil" --description "Personal productivity and calendar" --input build\input --main-jar focusflow.jar --main-class com.focusflow.app.Main --icon build\focusflow.ico --dest dist --license-file LICENSE --win-menu --win-shortcut --win-per-user-install --win-menu-group FocusFlow --win-upgrade-uuid cbdde24b-33ca-4119-8bc7-098ec045c41a --add-modules %MODS% --java-options "-Xmx384m"
if errorlevel 1 (
  echo.
  echo The .exe installer needs the WiX Toolset 3.x. Building a portable app folder instead...
  jpackage --type app-image --name FocusFlow --app-version 1.0.0 --vendor "Shibil" --input build\input --main-jar focusflow.jar --main-class com.focusflow.app.Main --icon build\focusflow.ico --dest dist --add-modules %MODS% --java-options "-Xmx384m"
  if errorlevel 1 goto fail
  echo Portable app: dist\FocusFlow\FocusFlow.exe  ^(zip this whole folder to share it^)
  goto done
)
echo Installer created in the dist folder.
:done
endlocal
exit /b 0

:fail
echo.
echo Packaging failed. Make sure JDK 21 and Maven are installed and on your PATH.
pause
exit /b 1
