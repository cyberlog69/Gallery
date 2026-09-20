@echo off
setlocal

if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_EXE=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
) else (
    set "JAVA_EXE=java"
)

set "JAR_PATH=%~dp0desktopApp\build\compose\jars\PicasaGalleryViewer-windows-x64-1.0.0.jar"

if not exist "%JAR_PATH%" (
    echo Building Picasa Photo Viewer desktop application...
    call "%~dp0gradlew.bat" :desktopApp:packageUberJarForCurrentOS
)

echo Launching Picasa Photo Viewer...
"%JAVA_EXE%" -jar "%JAR_PATH%" %*
endlocal
