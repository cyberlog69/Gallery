@echo off
setlocal
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
)
echo Building Android Gallery APK...
call "%~dp0gradlew.bat" :androidApp:assembleDebug
echo APK location: %~dp0androidApp\build\outputs\apk\debug\androidApp-debug.apk
endlocal
