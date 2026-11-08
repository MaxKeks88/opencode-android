package ru.opencode.android;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * Мост к Shizuku: даёт shell-права (uid 2000) после разового запуска Shizuku
 * через ADB или беспроводную отладку. Без Shizuku методы просто возвращают
 * «недоступно».
 */
public final class ShizukuBridge {
    public static final int REQUEST_CODE = 4201;
    private static final String PACKAGE = "moe.shizuku.privileged.api";
    private static volatile boolean listenersAdded = false;

    private ShizukuBridge() {
    }

    public static void init() {
        if (listenersAdded) {
            return;
        }
        listenersAdded = true;
        try {
            Shizuku.addBinderReceivedListenerSticky(new Shizuku.OnBinderReceivedListener() {
                @Override // rikka.shizuku.Shizuku.OnBinderReceivedListener
                public void onBinderReceived() {
                    try {
                        App.log("shizuku: binder получен, uid=" + Shizuku.getUid());
                    } catch (Throwable th) {
                    }
                }
            });
            Shizuku.addBinderDeadListener(new Shizuku.OnBinderDeadListener() {
                @Override // rikka.shizuku.Shizuku.OnBinderDeadListener
                public void onBinderDead() {
                    App.log("shizuku: binder потерян");
                }
            });
        } catch (Throwable th) {
            App.log("shizuku init: " + String.valueOf(th));
        }
    }

    public static boolean installed(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    public static boolean running() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable th) {
            return false;
        }
    }

    public static boolean granted() {
        try {
            return running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable th) {
            return false;
        }
    }

    public static int uid() {
        try {
            return running() ? Shizuku.getUid() : -1;
        } catch (Throwable th) {
            return -1;
        }
    }

    public static int version() {
        try {
            return running() ? Shizuku.getVersion() : -1;
        } catch (Throwable th) {
            return -1;
        }
    }

    public static void requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (Throwable th) {
            App.log("shizuku request: " + String.valueOf(th));
        }
    }

    public static Result exec(String command, String cwd) throws Exception {
        if (!granted()) {
            throw new SecurityException("Полный доступ (Shizuku) не включён");
        }
        IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
        if (service == null) {
            throw new IllegalStateException("Shizuku не запущен");
        }
        String dir = cwd == null || cwd.isEmpty() ? "/" : cwd;
        // shell (uid 2000) не имеет доступа к /data приложения — уходим в общую память
        if (dir.startsWith("/data") || dir.startsWith("/storage/emulated/0/Android/data")) {
            dir = "/sdcard";
        }
        IRemoteProcess process = service.newProcess(new String[]{"sh", "-c", command}, null, dir);
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Thread tOut = reader(process.getInputStream(), out);
        Thread tErr = reader(process.getErrorStream(), err);
        int code = process.waitFor();
        tOut.join(5000L);
        tErr.join(5000L);
        String text = out.toString();
        if (err.length() > 0) {
            text = text.isEmpty() ? err.toString() : text + "\n" + err;
        }
        return new Result(text, code);
    }

    private static Thread reader(final ParcelFileDescriptor pfd, final StringBuilder sb) {
        Thread thread = new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) > 0) {
                        sb.append(new String(buf, 0, read, StandardCharsets.UTF_8));
                    }
                } catch (Throwable th) {
                }
            }
        }, "ocsh-read");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    public static final class Result {
        public final String out;
        public final int code;

        Result(String out, int code) {
            this.out = out;
            this.code = code;
        }
    }
}
