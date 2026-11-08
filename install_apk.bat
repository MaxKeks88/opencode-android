@echo off
chcp 65001 >nul
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb
"%ADB%" install --no-incremental -r -d "%~dp0OpenCodeAndroid.apk"
echo.
pause
