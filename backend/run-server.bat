@echo off
setlocal
cd /d "%~dp0"
set "PORT=%~1"
if "%PORT%"=="" set "PORT=8765"
if not exist out mkdir out
javac --add-modules jdk.jdi,jdk.httpserver -d out src\main\java\visualizer\*.java
if errorlevel 1 (
  echo Could not compile the local visualizer. Check that JDK 17 or later is installed.
  pause
  exit /b 1
)
java --add-modules jdk.jdi,jdk.httpserver -cp out visualizer.LocalServer "%~dp0..\frontend" "%PORT%"
pause
endlocal
