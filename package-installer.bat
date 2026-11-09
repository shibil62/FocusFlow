@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo  FocusFlow - building the single-file installer
echo ============================================

rem ============================================================
rem 0. Find WiX Toolset 3.x automatically
rem ============================================================

set "WIX_BIN=C:\Program Files (x86)\WiX Toolset v3.14\bin"

rem Add WiX to PATH if installed in the standard location
if exist "%WIX_BIN%\candle.exe" (
    set "PATH=%WIX_BIN%;%PATH%"
)

rem Verify WiX is now available
where candle.exe >nul 2>nul
if errorlevel 1 (
    echo.
    echo ERROR: WiX Toolset 3.x was not found.
    echo.
    echo Expected location:
    echo   C:\Program Files ^(x86^)\WiX Toolset v3.14\bin
    echo.
    echo Please install WiX Toolset 3.14 and try again.
    goto fail
)

where light.exe >nul 2>nul
if errorlevel 1 (
    echo.
    echo ERROR: WiX Toolset is incomplete. light.exe was not found.
    goto fail
)

echo WiX Toolset found:
where candle.exe

rem ============================================================
rem 1. Make sure FocusFlow is not running
rem ============================================================

taskkill /F /IM FocusFlow.exe >nul 2>nul
taskkill /F /IM javaw.exe >nul 2>nul

echo.
echo [1/4] Building the JAR...

call mvn -q clean package
if errorlevel 1 goto fail

rem ============================================================
rem 2. Generate the application icon
rem ============================================================

echo [2/4] Generating the application icon...

if not exist build mkdir build

java -cp target\classes com.focusflow.app.IconExporter build\focusflow.ico
if errorlevel 1 goto fail

rem ============================================================
rem 3. Prepare jpackage input
rem ============================================================

echo [3/4] Preparing the jpackage input folder...

if exist build\input rmdir /s /q build\input
mkdir build\input

copy /y target\focusflow.jar build\input\ >nul
if errorlevel 1 goto fail

rem ============================================================
rem 4. Remove old output and create installer
rem ============================================================

if exist dist rmdir /s /q dist
mkdir dist

echo [4/4] Running jpackage (this takes a minute)...

set "MODS=java.desktop,java.sql,java.logging,java.naming,java.management,java.prefs,jdk.unsupported"

rem The upgrade UUID must NEVER change.
rem It allows newer versions to replace older installations.

jpackage ^
  --type exe ^
  --name FocusFlow ^
  --app-version 1.1.0 ^
  --vendor "Shibil" ^
  --description "Personal productivity and calendar" ^
  --input build\input ^
  --main-jar focusflow.jar ^
  --main-class com.focusflow.app.Main ^
  --icon build\focusflow.ico ^
  --dest dist ^
  --license-file LICENSE ^
  --win-menu ^
  --win-shortcut ^
  --win-per-user-install ^
  --win-menu-group FocusFlow ^
  --win-upgrade-uuid cbdde24b-33ca-4119-8bc7-098ec045c41a ^
  --add-modules %MODS% ^
  --java-options "-Xmx384m"

if errorlevel 1 goto fail

rem ============================================================
rem Verify installer was created
rem ============================================================

if not exist "dist\FocusFlow-1.1.0.exe" (
    echo.
    echo ERROR: jpackage finished but the installer was not created.
    echo Expected:
    echo   dist\FocusFlow-1.1.0.exe
    goto fail
)

move /y "dist\FocusFlow-1.1.0.exe" "dist\FocusFlow.exe" >nul

echo.
echo ============================================
echo DONE!
echo ============================================
echo.
echo Your single-file installer is:
echo.
echo   %cd%\dist\FocusFlow.exe
echo.
echo It includes the Java runtime.
echo Your friends do NOT need Java installed.
echo.

goto done


:fail
echo.
echo ============================================
echo Packaging failed.
echo ============================================
echo.
pause
exit /b 1


:done
endlocal
exit /b 0