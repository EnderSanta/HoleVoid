@echo off
setlocal
cd /d "%~dp0"
echo === HoleVoid build for Paper 1.21.11 ===
where java >nul 2>nul || (echo ERROR: Java not found. Install Java 21. & pause & exit /b 1)
where mvn >nul 2>nul || (echo ERROR: Maven not found. Install Maven 3.9+ and add it to PATH. & pause & exit /b 1)
java -version
mvn -U clean package
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Check the Maven output above and verify internet access.
  pause
  exit /b 1
)
echo.
echo BUILD SUCCESS. JAR: target\holevoid-1.0.0.jar
echo Copy that JAR into your Paper server's plugins folder.
pause
