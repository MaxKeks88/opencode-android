package ru.opencode.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Результат установки APK через PackageInstaller. */
public class UpdateReceiver extends BroadcastReceiver {
    public static final String ACTION = "ru.opencode.android.UPDATE_RESULT";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        App.log("app-update: установка status=" + status + " " + String.valueOf(message));
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Notifications.show(context, "OpenCode", "Обновление установлено");
        } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // система сама показывает диалог; на всякий случай пробуем открыть
            try {
                Intent confirm = (Intent) intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                }
            } catch (Throwable th) {
                App.log("app-update: подтверждение установки: " + th);
            }
        } else {
            Notifications.show(context, "OpenCode", "Не удалось установить обновление");
        }
    }
}
