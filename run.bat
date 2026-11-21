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
start "" javaw -Xms16m -Xmx192m -XX:+UseSerialGC -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=15 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=32m -XX:MaxMetaspaceSize=96m -Xss512k -XX:-UsePerfData -Dsun.java2d.d3d=false -jar "target\focusflow.jar"
endlocal
