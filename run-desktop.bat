@echo off
setlocal

if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_EXE=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
) else (
    set "JAVA_EXE=java"
)

for %%F in ("%~dp0desktopApp\build\compose\jars\PicasaGalleryViewer-windows-x64-*.jar") do set "JAR_PATH=%%F"

if not defined JAR_PATH (
    echo Building Picasa Photo Viewer desktop application...
    call "%~dp0gradlew.bat" :desktopApp:packageUberJarForCurrentOS
    for %%F in ("%~dp0desktopApp\build\compose\jars\PicasaGalleryViewer-windows-x64-*.jar") do set "JAR_PATH=%%F"
)

echo Launching Picasa Photo Viewer...
"%JAVA_EXE%" -jar "%JAR_PATH%" %*
endlocal
