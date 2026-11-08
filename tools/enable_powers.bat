@echo off
rem Выдаёт приложению OpenCode Android расширенные development-права через ADB.
rem Запускать ОДИН РАЗ с ПК, когда телефон подключён по USB и отладка включена.
setlocal
set "PKG=ru.opencode.android"
set "ADB=adb"
where adb >nul 2>&1 || set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo Не найден adb. Укажите путь в переменной ANDROID_SDK_ROOT или установите platform-tools.
  pause
  exit /b 1
)

echo Устройства:
"%ADB%" devices
echo.

call :grant android.permission.WRITE_SECURE_SETTINGS
call :grant android.permission.WRITE_SETTINGS
call :grant android.permission.DUMP
call :grant android.permission.READ_LOGS
call :grant android.permission.INTERACT_ACROSS_USERS
call :grant android.permission.MANAGE_APP_OPS_MODES
call :grant android.permission.PACKAGE_USAGE_STATS

echo.
echo appops GET_USAGE_STATS:
"%ADB%" shell appops set %PKG% GET_USAGE_STATS allow
echo.

echo Проверка (должны появиться строки granted=true):
"%ADB%" shell dumpsys package %PKG% ^| findstr /C:"WRITE_SECURE_SETTINGS" /C:"DUMP" /C:"READ_LOGS" /C:"INTERACT_ACROSS_USERS"
echo.
echo Готово. Перезапустите приложение OpenCode.
pause
exit /b 0

:grant
echo pm grant %~1
"%ADB%" shell pm grant %PKG% %~1
exit /b 0
