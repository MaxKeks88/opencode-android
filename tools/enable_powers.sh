#!/bin/sh
# Выдаёт приложению OpenCode Android расширенные development-права через ADB.
# Один раз: телефон по USB, отладка включена. Linux/macOS.
set -e
PKG=ru.opencode.android
ADB="${ADB:-adb}"

echo "Устройства:"
$ADB devices
echo

for perm in \
  android.permission.WRITE_SECURE_SETTINGS \
  android.permission.WRITE_SETTINGS \
  android.permission.DUMP \
  android.permission.READ_LOGS \
  android.permission.INTERACT_ACROSS_USERS \
  android.permission.MANAGE_APP_OPS_MODES \
  android.permission.PACKAGE_USAGE_STATS
do
  echo "pm grant $perm"
  $ADB shell pm grant "$PKG" "$perm" || echo "  (не выдано: $perm)"
done

echo
$ADB shell appops set "$PKG" GET_USAGE_STATS allow || true
echo
echo "Проверка:"
$ADB shell dumpsys package "$PKG" | grep -E "WRITE_SECURE_SETTINGS|DUMP|READ_LOGS|INTERACT_ACROSS_USERS" || true
echo
echo "Готово. Перезапустите приложение OpenCode."
