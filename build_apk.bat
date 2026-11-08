@echo off
chcp 65001 >nul
cd /d "%~dp0"
python build_apk.py %*
echo.
echo APK: %~dp0OpenCodeAndroid.apk
pause
