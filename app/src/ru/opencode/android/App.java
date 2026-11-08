package ru.opencode.android;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/* JADX INFO: loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public class App extends Application {
    public static final int HTTP_PORT = 8769;
    public static final String PREFS = "opencode";
    public static final String TAG = "OpenCode";
    public static File bin;
    public static File home;
    private static App inst;
    public static File lib;
    public static File logs;
    public static File projects;
    public static File root;
    public static File tmp;
    public static File uploads;
    public static File usr;
    public static File web;
    public static final int OC_PORT = 4096;
    public static volatile int ocPort = OC_PORT;
    public static String password = "";
    public static String localToken = "";
    public static volatile boolean ocRunning = false;
    public static volatile boolean serverRunning = false;
    public static volatile String lastError = "";

    public static App get() {
        return inst;
    }

    public static String versionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable th) {
            return "";
        }
    }

    /** Собирает ZIP с логами и сведениями об устройстве в /sdcard/Download.
     *  Возвращает путь к файлу или строку с ошибкой. */
    public static String exportLogsZip() {
        try {
            File dir = new File("/sdcard/Download");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File zip = new File(dir, "opencode-logs-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".zip");
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)));
            byte[] buf = new byte[65536];
            String[] names = {"app.log", "app.old.log", "opencode.log"};
            for (String name : names) {
                File file = new File(logs, name);
                if (!file.isFile()) {
                    continue;
                }
                zos.putNextEntry(new ZipEntry(name));
                FileInputStream in = new FileInputStream(file);
                int read;
                while ((read = in.read(buf)) > 0) {
                    zos.write(buf, 0, read);
                }
                in.close();
                zos.closeEntry();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("OpenCode Android ").append(inst == null ? "" : versionName(inst)).append('\n');
            sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
            sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
            sb.append("ABI: ").append(Build.SUPPORTED_ABIS[0]).append('\n');
            sb.append("OpenCode: port=").append(ocPort).append(" running=").append(ocRunning).append(" server=").append(serverRunning).append('\n');
            sb.append("Last error: ").append(lastError).append('\n');
            sb.append("Home: ").append(home).append('\n');
            sb.append("Prefs: notify=").append(prefs().getBoolean("notify", true))
              .append(" sound=").append(prefs().getBoolean("sound", false))
              .append(" vib=").append(prefs().getBoolean("vib", true))
              .append(" keep_screen_on=").append(prefs().getBoolean("keep_screen_on", false)).append('\n');
            zos.putNextEntry(new ZipEntry("device.txt"));
            zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.close();
            return zip.getAbsolutePath();
        } catch (Throwable th) {
            return "Ошибка экспорта логов: " + String.valueOf(th);
        }
    }

    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        inst = this;
        initPaths();
        SharedPreferences sharedPreferencesPrefs = prefs();
        password = sharedPreferencesPrefs.getString("password", "");
        if (password == null || password.isEmpty()) {
            password = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            sharedPreferencesPrefs.edit().putString("password", password).apply();
        }
        localToken = sharedPreferencesPrefs.getString("local_token", "");
        if (localToken == null || localToken.isEmpty()) {
            localToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            sharedPreferencesPrefs.edit().putString("local_token", localToken).apply();
        }
        log("=== OpenCode Android старт, sdk=" + Build.VERSION.SDK_INT + ", abi=" + Build.SUPPORTED_ABIS[0] + " ===");
        final Thread.UncaughtExceptionHandler prevHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override // java.lang.Thread.UncaughtExceptionHandler
            public void uncaughtException(Thread thread, Throwable th) {
                try {
                    log("КРАШ в потоке " + thread.getName() + ": " + Log.getStackTraceString(th));
                } catch (Throwable th2) {
                }
                if (prevHandler != null) {
                    prevHandler.uncaughtException(thread, th);
                }
            }
        });
    }

    private void initPaths() {
        root = getFilesDir();
        usr = new File(root, "usr");
        bin = new File(usr, "bin");
        lib = new File(usr, "lib");
        home = new File(root, "home");
        tmp = new File(root, "tmp");
        logs = new File(root, "logs");
        uploads = new File(root, "uploads");
        web = new File(root, "web");
        projects = new File(root, "projects");
        File[] fileArr = {usr, bin, lib, home, tmp, logs, uploads, web, projects};
        for (int i = 0; i < 9; i++) {
            File file = fileArr[i];
            if (!file.exists()) {
                file.mkdirs();
            }
        }
    }

    public static SharedPreferences prefs() {
        return inst.getSharedPreferences(PREFS, 0);
    }

    public static File loader() {
        return new File(lib, "ld-musl-aarch64.so.1");
    }

    public static File opencode() {
        return new File(bin, PREFS);
    }

    public static String defaultDirectory() {
        String string = prefs().getString("workdir", "");
        return (string == null || string.isEmpty()) ? new File(Environment.getExternalStorageDirectory(), TAG).getAbsolutePath() : string;
    }

    public static void setDefaultDirectory(String str) {
        prefs().edit().putString("workdir", str).apply();
    }

    public static boolean allFilesGranted() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                return Environment.isExternalStorageManager();
            } catch (Throwable th) {
                return false;
            }
        }
        return true;
    }

    public static void makeExecutable(File file) {
        try {
            file.setReadable(true, false);
            file.setExecutable(true, false);
        } catch (Throwable th) {
        }
    }

    public static synchronized void log(String str) {
        Log.i(TAG, str);
        try {
            if (logs == null) {
                return;
            }
            File file = new File(logs, "app.log");
            if (file.length() > 2097152) {
                File file2 = new File(logs, "app.old.log");
                if (file2.exists()) {
                    file2.delete();
                }
                file.renameTo(file2);
            }
            FileWriter fileWriter = new FileWriter(file, true);
            fileWriter.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "  " + str + "\n");
            fileWriter.close();
        } catch (Throwable th) {
        }
    }
}
