package ru.opencode.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/* JADX INFO: loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public final class Notifications {
    private static final String CHANNEL = "messages";
    private static final String CHANNEL_QUIET = "asks";
    public static volatile long lastShownAt = 0;

    private Notifications() {
    }

    public static void show(Context context, String str, String str2) {
        show(context, str, str2, false);
    }

    /* Тихий вариант: запросы/формы — без вибрации и звука. */
    public static void showQuiet(Context context, String str, String str2) {
        show(context, str, str2, true);
    }

    private static void show(Context context, String str, String str2, boolean quiet) {
        try {
            if (!App.prefs().getBoolean("notify", true)) {
                return;
            }
            NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
            NotificationChannel notificationChannel = new NotificationChannel(
                    quiet ? CHANNEL_QUIET : CHANNEL,
                    Lang.tr(quiet ? "Запросы OpenCode" : "Сообщения OpenCode"),
                    quiet ? 2 : 3);
            if (quiet) {
                notificationChannel.setSound(null, null);
                notificationChannel.enableVibration(false);
                notificationChannel.setShowBadge(false);
            } else {
                notificationChannel.setShowBadge(true);
            }
            notificationManager.createNotificationChannel(notificationChannel);
            Intent intent = new Intent(context, (Class<?>) MainActivity.class);
            intent.setFlags(536870912);
            PendingIntent activity = PendingIntent.getActivity(context, 10, intent, 201326592);
            Notification.Builder builder = new Notification.Builder(context, quiet ? CHANNEL_QUIET : CHANNEL);
            if (str == null || str.isEmpty()) {
                str = App.TAG;
            }
            str = Lang.tr(str);
            if (str2 == null) {
                str2 = "";
            }
            str2 = Lang.tr(str2);
            Notification.Builder contentText = builder.setContentTitle(str).setContentText(str2);
            Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle();
            if (str2 == null) {
                str2 = "";
            }
            contentText.setStyle(bigTextStyle.bigText(str2)).setSmallIcon(android.R.drawable.stat_notify_chat).setAutoCancel(true).setContentIntent(activity);
            lastShownAt = System.currentTimeMillis();
            notificationManager.notify(quiet ? 1002 : 1001, builder.build());
        } catch (Throwable th) {
            App.log("notification: " + String.valueOf(th));
        }
    }
}
