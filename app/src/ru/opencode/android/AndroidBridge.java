package ru.opencode.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.app.AppOpsManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;

/* JADX INFO: loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public class AndroidBridge {
    private static final int REQ_NOTIFY = 101;
    private static final int REQ_STORAGE = 100;
    private final MainActivity act;

    AndroidBridge(MainActivity mainActivity) {
        this.act = mainActivity;
    }

    @JavascriptInterface
    public void notify(String str, String str2) {
        Notifications.show(this.act, str, str2);
    }

    @JavascriptInterface
    public void vibrate(int i) {
        try {
            Vibrator vibrator = (Vibrator) this.act.getSystemService("vibrator");
            if (vibrator == null) {
                return;
            }
            vibrator.vibrate(VibrationEffect.createOneShot(Math.max(20, Math.min(600, i)), -1));
        } catch (Throwable th) {
        }
    }

    @JavascriptInterface
    public void copyText(final String str) {
        this.act.runOnUiThread(new Runnable() { // from class: ru.opencode.android.AndroidBridge.1
            @Override // java.lang.Runnable
            public void run() {
                try {
                    ((ClipboardManager) AndroidBridge.this.act.getSystemService("clipboard")).setPrimaryClip(ClipData.newPlainText(App.TAG, str));
                } catch (Throwable th) {
                }
            }
        });
    }

    @JavascriptInterface
    public void openUrl(String str) {
        try {
            this.act.startActivity(new Intent("android.intent.action.VIEW", Uri.parse(str)));
        } catch (Throwable th) {
            App.log("openUrl: " + String.valueOf(th));
        }
    }

    private boolean batteryOk() {
        try {
            PowerManager powerManager = (PowerManager) this.act.getSystemService(Context.POWER_SERVICE);
            return powerManager == null || powerManager.isIgnoringBatteryOptimizations(this.act.getPackageName());
        } catch (Throwable th) {
            return false;
        }
    }

    private boolean granted(String str) {
        try {
            return this.act.checkSelfPermission(str) == 0;
        } catch (Throwable th) {
            return false;
        }
    }

    private boolean usageAccess() {
        try {
            AppOpsManager appOpsManager = (AppOpsManager) this.act.getSystemService(Context.APP_OPS_SERVICE);
            return appOpsManager != null && appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), this.act.getPackageName()) == 0;
        } catch (Throwable th) {
            return false;
        }
    }

    @JavascriptInterface
    public String getSettings() {        try {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("notify", App.prefs().getBoolean("notify", true));
            jSONObject.put("sound", App.prefs().getBoolean("sound", false));
            jSONObject.put("vib", App.prefs().getBoolean("vib", true));
            jSONObject.put("keep_screen_on", App.prefs().getBoolean("keep_screen_on", false));
            jSONObject.put("allFiles", allFiles());
            jSONObject.put("notif", Build.VERSION.SDK_INT < 33 || this.act.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0);
            jSONObject.put("battery", batteryOk());
            boolean z;
            if (Build.VERSION.SDK_INT >= 30) {
                z = allFiles();
            } else {
                z = this.act.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == 0;
            }
            jSONObject.put("storage", z);
            jSONObject.put("dev", granted("android.permission.WRITE_SECURE_SETTINGS"));
            jSONObject.put("dump", granted("android.permission.DUMP"));
            jSONObject.put("logs", granted("android.permission.READ_LOGS"));
            jSONObject.put("usage", usageAccess());
            return jSONObject.toString();
        } catch (Throwable th) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void saveSetting(String str, boolean z) {
        App.prefs().edit().putBoolean(str, z).apply();
    }

    /* совместимость со старыми версиями страницы */
    @JavascriptInterface
    public void setSetting(String str, boolean z) {
        saveSetting(str, z);
    }

    @JavascriptInterface
    public String getLang() {
        return Lang.get();
    }

    @JavascriptInterface
    public void setLang(String code) {
        if (Lang.EN.equals(code) || Lang.RU.equals(code) || Lang.ZH.equals(code)
                || Lang.ZH_HANT.equals(code) || Lang.ES.equals(code)
                || Lang.PT.equals(code)) {
            Lang.set(code);
        }
    }

    @JavascriptInterface
    public String appUpdateState() {
        return AppUpdater.state(this.act).toString();
    }

    @JavascriptInterface
    public String appUpdateCheck(boolean force) {
        AppUpdater.checkAsync(this.act, force, new AppUpdater.Callback() {
            @Override // ru.opencode.android.AppUpdater.Callback
            public void done(String text) {
                App.log("app-update: " + text);
            }
        });
        return AppUpdater.state(this.act).toString();
    }

    @JavascriptInterface
    public String appUpdateInstall() {
        AppUpdater.installAsync(this.act, new AppUpdater.Callback() {
            @Override // ru.opencode.android.AppUpdater.Callback
            public void done(String text) {
                App.log("app-update: " + text);
            }
        });
        return AppUpdater.state(this.act).toString();
    }

    @JavascriptInterface
    public void appUpdateOpenPage() {
        openUrl(AppUpdater.RELEASE_URL);
    }

    @JavascriptInterface
    public void appUpdateOpenInstallSettings() {
        AppUpdater.openInstallSettings(this.act);
    }

    @JavascriptInterface
    public boolean appUpdateAllowed() {
        return AppUpdater.allowedToInstall(this.act);
    }

    @JavascriptInterface
    public void appUpdateAuto(boolean enabled) {
        AppUpdater.setAutoEnabled(enabled);
    }

    @JavascriptInterface
    public void openSettings() {
        this.act.runOnUiThread(new Runnable() { // from class: ru.opencode.android.AndroidBridge.2
            @Override // java.lang.Runnable
            public void run() {
                AndroidBridge.this.act.startActivity(new Intent(AndroidBridge.this.act, (Class<?>) SettingsActivity.class));
            }
        });
    }

    @JavascriptInterface
    public void log(String str) {
        App.log("PAGE: " + str);
    }

    @JavascriptInterface
    public String home() {
        return App.defaultDirectory();
    }

    @JavascriptInterface
    public String version() {
        return App.versionName(this.act);
    }

    @JavascriptInterface
    public String exportLogs() {
        return App.exportLogsZip();
    }

    @JavascriptInterface
    public String shizukuStatus() {
        try {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("installed", ShizukuBridge.installed(this.act));
            jSONObject.put("running", ShizukuBridge.running());
            jSONObject.put("granted", ShizukuBridge.granted());
            jSONObject.put("uid", ShizukuBridge.uid());
            jSONObject.put("version", ShizukuBridge.version());
            return jSONObject.toString();
        } catch (Throwable th) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void shizukuRequest() {
        this.act.runOnUiThread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                ShizukuBridge.requestPermission();
            }
        });
    }

    @JavascriptInterface
    public void openShizuku() {
        this.act.runOnUiThread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                try {
                    Intent intent = AndroidBridge.this.act.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (intent != null) {
                        AndroidBridge.this.act.startActivity(intent);
                        return;
                    }
                } catch (Throwable th) {
                }
                try {
                    AndroidBridge.this.act.startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://shizuku.rikka.app/download/")));
                } catch (Throwable th2) {
                    App.log("openShizuku: " + String.valueOf(th2));
                }
            }
        });
    }

    @JavascriptInterface
    public void openDeveloperOptions() {
        this.act.runOnUiThread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                try {
                    AndroidBridge.this.act.startActivity(new Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"));
                } catch (Throwable th) {
                    try {
                        AndroidBridge.this.act.startActivity(new Intent("android.settings.SETTINGS"));
                    } catch (Throwable th2) {
                        App.log("openDeveloperOptions: " + String.valueOf(th2));
                    }
                }
            }
        });
    }

    @JavascriptInterface
    public void openWirelessDebugging() {
        this.act.runOnUiThread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                try {
                    AndroidBridge.this.act.startActivity(new Intent("android.settings.WIRELESS_DEBUGGING"));
                } catch (Throwable th) {
                    try {
                        AndroidBridge.this.act.startActivity(new Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"));
                    } catch (Throwable th2) {
                        App.log("openWirelessDebugging: " + String.valueOf(th2));
                    }
                }
            }
        });
    }

    @JavascriptInterface
    public void pickFile() {
        this.act.runOnUiThread(new Runnable() { // from class: ru.opencode.android.AndroidBridge.3
            @Override // java.lang.Runnable
            public void run() {
                try {
                    Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
                    intent.addCategory("android.intent.category.OPENABLE");
                    intent.setType("*/*");
                    AndroidBridge.this.act.startActivityForResult(intent, 102);
                } catch (Throwable th) {
                    App.log("pickFile: " + String.valueOf(th));
                }
            }
        });
    }

    @JavascriptInterface
    public void requestStorage() {
        this.act.runOnUiThread(new Runnable() { // from class: ru.opencode.android.AndroidBridge.4
            @Override // java.lang.Runnable
            public void run() {
                if (Build.VERSION.SDK_INT >= 30) {
                    AndroidBridge.this.act.openAllFilesSettings();
                    return;
                }
                String[] perms = {"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"};
                AndroidBridge.this.act.requestPermissions(perms, REQ_STORAGE);
            }
        });
    }

    @JavascriptInterface
    public void openNotifSettings() {
        this.act.runOnUiThread(new Runnable() { // from class: ru.opencode.android.AndroidBridge.5
            @Override // java.lang.Runnable
            public void run() {
                try {
                    if (Build.VERSION.SDK_INT >= 33 && AndroidBridge.this.act.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                        AndroidBridge.this.act.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
                        return;
                    }
                    Intent intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
                    intent.putExtra("android.provider.extra.APP_PACKAGE", AndroidBridge.this.act.getPackageName());
                    AndroidBridge.this.act.startActivity(intent);
                } catch (Throwable th) {
                    App.log("openNotifSettings: " + String.valueOf(th));
                }
            }
        });
    }

    @JavascriptInterface
    public void openBatterySettings() {
        this.act.runOnUiThread(new Runnable() { // from class: ru.opencode.android.AndroidBridge.6
            @Override // java.lang.Runnable
            public void run() {
                try {
                    Intent intent = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", Uri.parse("package:" + AndroidBridge.this.act.getPackageName()));
                    AndroidBridge.this.act.startActivity(intent);
                } catch (Throwable th) {
                    try {
                        AndroidBridge.this.act.startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
                    } catch (Throwable th2) {
                        App.log("openBatterySettings: " + String.valueOf(th2));
                    }
                }
            }
        });
    }

    @JavascriptInterface
    public boolean allFiles() {
        return App.allFilesGranted();
    }
}
