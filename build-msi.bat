@echo off
setlocal
echo ==========================================================
echo  Building Picasa Photo Viewer Windows MSI Installer (.msi)
echo ==========================================================

rem Ensure Gradle uses JDK 21 with jpackage
set "JAVA_HOME=%USERPROFILE%\.jdks\jbr-21.0.11"
set "PATH=%JAVA_HOME%\bin;%PATH%"

call gradlew.bat :desktopApp:packageMsi

if %ERRORLEVEL% equ 0 (
    echo.
    echo ==========================================================
    echo  MSI Installer generated successfully!
    echo  Location: desktopApp\build\compose\binaries\main\msi\PicasaGalleryViewer-1.0.1.msi
    echo ==========================================================
) else (
    echo.
    echo [ERROR] MSI packaging failed.
)

pause
