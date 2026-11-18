@echo off
rem ===================================================================
rem  Wave Function Collapse - Windows launcher
rem
rem  Requirements:
rem    - JDK 17 or newer
rem    - JavaFX SDK, pointed to by JFX_HOME
rem
rem  Usage:
rem    run.bat [--width=800] [--height=800] [--set=circuit] [--seed=42]
rem
rem  Example with custom locations:
rem    set JFX_HOME=D:\tools\javafx-sdk-21
rem    set JDK_HOME=C:\Program Files\Java\jdk-17.0.20.1
rem    run.bat
rem ===================================================================
setlocal

rem ----- JavaFX SDK -------------------------------------------------
if not defined JFX_HOME set "JFX_HOME=C:\Program Files\Java\javafx-sdk-21.0.8"
set "JFX_LIB=%JFX_HOME%\lib"
set "MODULES=javafx.controls"

rem ----- JDK 17 or newer --------------------------------------------
rem JDK_HOME wins, then a known JDK 17 install, then JAVA_HOME. When none
rem of them is usable, whatever javac/java are on PATH is used as is.
set "JDK_FOUND="
if defined JDK_HOME if exist "%JDK_HOME%\bin\javac.exe" set "JDK_FOUND=%JDK_HOME%"
if not defined JDK_FOUND if exist "C:\Program Files\Java\jdk-17.0.20.1\bin\javac.exe" set "JDK_FOUND=C:\Program Files\Java\jdk-17.0.20.1"
if not defined JDK_FOUND if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDK_FOUND=%JAVA_HOME%"
if defined JDK_FOUND set "PATH=%JDK_FOUND%\bin;%PATH%"

set "CLASSES=out"

rem ----- Checks ----------------------------------------------------
if not exist "%JFX_LIB%\javafx.controls.jar" (
    echo [ERROR] JavaFX was not found in "%JFX_LIB%"
    echo         Set JFX_HOME to the folder of the JavaFX SDK, for example:
    echo             set JFX_HOME=C:\Program Files\Java\javafx-sdk-21.0.8
    exit /b 1
)

where javac >nul 2>nul
if errorlevel 1 (
    echo [ERROR] javac was not found. Install JDK 17 or newer and set JDK_HOME.
    exit /b 1
)

rem ----- Build -----------------------------------------------------
echo Compiling sources...
if not exist "%CLASSES%" mkdir "%CLASSES%"
javac -encoding UTF-8 -d "%CLASSES%" --module-path "%JFX_LIB%" --add-modules %MODULES% src\principal\*.java
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    echo         A JDK 17 or newer is required. If "javac -version" reports 1.8,
    echo         point JDK_HOME to your JDK 17+ installation.
    exit /b 1
)

rem ----- Run -------------------------------------------------------
echo Starting Wave Function Collapse...
java --module-path "%JFX_LIB%" --add-modules %MODULES% -cp "%CLASSES%;resources" principal.WaveFunction %*
exit /b %ERRORLEVEL%
