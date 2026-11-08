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
    public static volatile long lastShownAt = 0;

    private Notifications() {
    }

    public static void show(Context context, String str, String str2) {
        try {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL, "Сообщения OpenCode", 3);
            notificationChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(notificationChannel);
            Intent intent = new Intent(context, (Class<?>) MainActivity.class);
            intent.setFlags(536870912);
            PendingIntent activity = PendingIntent.getActivity(context, 10, intent, 201326592);
            Notification.Builder builder = new Notification.Builder(context, CHANNEL);
            if (str == null || str.isEmpty()) {
                str = App.TAG;
            }
            Notification.Builder contentText = builder.setContentTitle(str).setContentText(str2 == null ? "" : str2);
            Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle();
            if (str2 == null) {
                str2 = "";
            }
            contentText.setStyle(bigTextStyle.bigText(str2)).setSmallIcon(android.R.drawable.stat_notify_chat).setAutoCancel(true).setContentIntent(activity);
            lastShownAt = System.currentTimeMillis();
            notificationManager.notify((int) (System.currentTimeMillis() % 100000), builder.build());
        } catch (Throwable th) {
            App.log("notification: " + String.valueOf(th));
        }
    }
}
