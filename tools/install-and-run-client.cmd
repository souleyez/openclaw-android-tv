@echo off
set "ANDROID_SDK_ROOT=C:\Users\soulzyn\develop\android-sdk"
set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
set "APK=C:\Users\soulzyn\Desktop\codex\apps\android-tv-client\build\app\outputs\flutter-apk\app-debug.apk"

"%ADB%" wait-for-device
"%ADB%" install -r "%APK%"
"%ADB%" shell monkey -p com.openclaw.assistant.openclaw_android_tv_client -c android.intent.category.LAUNCHER 1
