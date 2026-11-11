package ru.opencode.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/* JADX INFO: loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public class BootReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if ("android.intent.action.BOOT_COMPLETED".equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            try {
                RuntimeService.startOnBoot(context);
            } catch (Throwable th) {
                App.log("boot start: " + String.valueOf(th));
            }
        }
    }
}
