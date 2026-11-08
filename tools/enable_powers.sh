#!/bin/sh
# Выдаёт приложению OpenCode Android development-права через ADB
# и, если установлен Shizuku, запускает его. Linux/macOS.
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
$ADB shell dumpsys package "$PKG" | grep -E "WRITE_SECURE_SETTINGS|READ_LOGS|INTERACT_ACROSS_USERS" || true
echo

if $ADB shell pm list packages | grep -q moe.shizuku.privileged.api; then
  echo "Запускаю Shizuku..."
  $ADB shell "APK=\$(pm path moe.shizuku.privileged.api | sed 's/package://'); DIR=\$(dirname \$APK); cp \$DIR/lib/arm64/libshizuku.so /data/local/tmp/shizuku 2>/dev/null; chmod 755 /data/local/tmp/shizuku 2>/dev/null; /data/local/tmp/shizuku 2>/dev/null || sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh" || true
  echo "Готово. Вернитесь в приложение: Настройки -> Разрешения -> Полные разрешения."
else
  echo "Shizuku не установлен. Если нужен полный доступ — установите Shizuku и повторите."
fi
echo
echo "Перезапустите приложение OpenCode."
