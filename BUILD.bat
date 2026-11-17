@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle 9.x is not installed. Install Gradle and Java 21, then run BUILD.bat again.
  pause
  exit /b 1
)
gradle build
if errorlevel 1 exit /b 1
echo.
echo Built JAR files:
dir /b build\libs\*.jar
echo Copy the remapped krypton-client JAR into %%appdata%%\.minecraft\mods
pause
