package ru.opencode.android;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Локальный сервис для команд, которым нужны права приложения (а не shell-пользователя):
 * запись системных настроек, список пакетов, открытие системных экранов.
 * Слушает только 127.0.0.1, порт и токен пишет в files/.occtl.
 * Клиент — files/usr/bin/occtl (Python), обёртка settings в PATH.
 */
public final class DeviceService {
    private static volatile boolean started = false;
    private static volatile ServerSocket server;

    private DeviceService() {
    }

    public static synchronized void start(final Context ctx) {
        if (started) {
            return;
        }
        started = true;
        new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                try {
                    ServerSocket socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
                    server = socket;
                    writeEndpoint(socket.getLocalPort());
                    App.log("occtl: 127.0.0.1:" + socket.getLocalPort());
                    while (!socket.isClosed()) {
                        final Socket client = socket.accept();
                        new Thread(new Runnable() {
                            @Override // java.lang.Runnable
                            public void run() {
                                serve(ctx, client);
                            }
                        }, "occtl-conn").start();
                    }
                } catch (Throwable th) {
                    App.log("occtl server: " + String.valueOf(th));
                }
            }
        }, "occtl").start();
    }

    public static synchronized void stop() {
        started = false;
        try {
            if (server != null) {
                server.close();
            }
        } catch (Throwable th) {
        }
        server = null;
        try {
            new File(App.root, ".occtl").delete();
        } catch (Throwable th2) {
        }
    }

    private static void writeEndpoint(int port) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(App.root, ".occtl"));
            fileOutputStream.write((port + ":" + App.localToken).getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
        } catch (Throwable th) {
            App.log("occtl endpoint: " + String.valueOf(th));
        }
    }

    private static void serve(Context ctx, Socket client) {
        try {
            client.setSoTimeout(30000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            JSONObject response = new JSONObject();
            try {
                JSONObject req = new JSONObject(line == null ? "{}" : line);
                if (!App.localToken.equals(req.optString("token"))) {
                    throw new SecurityException("bad token");
                }
                String cmd = req.optString("cmd");
                if ("sh".equals(cmd)) {
                    JSONArray array = req.optJSONArray("args");
                    String command = array == null || array.length() == 0 ? "" : array.optString(0);
                    ShizukuBridge.Result result = ShizukuBridge.exec(command, req.optString("cwd", "/"));
                    response.put("ok", true);
                    response.put("out", result.out);
                    response.put("code", result.code);
                } else {
                    response.put("ok", true);
                    response.put("out", execute(ctx, cmd, req.optJSONArray("args")));
                }
            } catch (Throwable th) {
                response = new JSONObject();
                response.put("ok", false);
                response.put("error", String.valueOf(th.getMessage() == null ? th : th.getMessage()));
            }
            OutputStreamWriter writer = new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(response.toString());
            writer.write("\n");
            writer.flush();
        } catch (Throwable th) {
            App.log("occtl serve: " + String.valueOf(th));
        } finally {
            try {
                client.close();
            } catch (Throwable th2) {
            }
        }
    }

    private static String[] args(JSONArray array) {
        if (array == null) {
            return new String[0];
        }
        String[] out = new String[array.length()];
        for (int i = 0; i < array.length(); i++) {
            out[i] = array.optString(i);
        }
        return out;
    }

    private static String execute(Context ctx, String cmd, JSONArray array) throws Exception {
        String[] a = args(array);
        if ("ping".equals(cmd)) {
            return "pong";
        }
        if ("sh-ready".equals(cmd)) {
            if (!ShizukuBridge.granted()) {
                throw new SecurityException("Полный доступ (Shizuku) не включён");
            }
            return "yes";
        }
        if ("settings".equals(cmd)) {
            return settings(ctx, a);
        }
        if ("pkg".equals(cmd)) {
            return pkg(ctx, a);
        }
        if ("app".equals(cmd)) {
            return app(ctx, a);
        }
        if ("toast".equals(cmd)) {
            toast(ctx, a.length > 0 ? a[0] : "");
            return "ok";
        }
        if ("notify".equals(cmd)) {
            Notifications.show(ctx, a.length > 0 ? a[0] : App.TAG, a.length > 1 ? a[1] : "");
            return "ok";
        }
        if ("info".equals(cmd)) {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("version", App.versionName(ctx));
            jSONObject.put("sdk", Build.VERSION.SDK_INT);
            jSONObject.put("model", Build.MANUFACTURER + " " + Build.MODEL);
            jSONObject.put("port", App.ocPort);
            jSONObject.put("running", App.ocRunning);
            return jSONObject.toString();
        }
        throw new IllegalArgumentException("unknown command: " + cmd);
    }

    private static String settings(Context ctx, String[] a) throws Exception {
        if (a.length < 2) {
            throw new IllegalArgumentException("usage: settings <get|put|delete|list> <system|secure|global> <key> [value]");
        }
        String op = a[0];
        String ns = a[1];
        if ("list".equals(op)) {
            android.database.Cursor cursor;
            if ("system".equals(ns)) {
                cursor = ctx.getContentResolver().query(Settings.System.CONTENT_URI, new String[]{"name", "value"}, null, null, null);
            } else if ("secure".equals(ns)) {
                cursor = ctx.getContentResolver().query(Settings.Secure.CONTENT_URI, new String[]{"name", "value"}, null, null, null);
            } else {
                cursor = ctx.getContentResolver().query(Settings.Global.CONTENT_URI, new String[]{"name", "value"}, null, null, null);
            }
            StringBuilder sb = new StringBuilder();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    sb.append(cursor.getString(0)).append('=').append(cursor.getString(1)).append('\n');
                }
                cursor.close();
            }
            return sb.toString();
        }
        String key = a.length > 2 ? a[2] : "";
        if ("get".equals(op)) {
            if ("system".equals(ns)) {
                return nullToEmpty(Settings.System.getString(ctx.getContentResolver(), key));
            }
            if ("secure".equals(ns)) {
                return nullToEmpty(Settings.Secure.getString(ctx.getContentResolver(), key));
            }
            return nullToEmpty(Settings.Global.getString(ctx.getContentResolver(), key));
        }
        if (!"put".equals(op) && !"delete".equals(op)) {
            throw new IllegalArgumentException("unknown settings op: " + op);
        }
        String value = "delete".equals(op) ? null : (a.length > 3 ? a[3] : "");
        boolean ok;
        if ("system".equals(ns)) {
            ok = Settings.System.putString(ctx.getContentResolver(), key, value);
        } else if ("secure".equals(ns)) {
            ok = Settings.Secure.putString(ctx.getContentResolver(), key, value);
        } else {
            ok = Settings.Global.putString(ctx.getContentResolver(), key, value);
        }
        return ok ? "ok" : "failed";
    }

    private static String pkg(Context ctx, String[] a) throws Exception {
        String op = a.length > 0 ? a[0] : "list";
        PackageManager pm = ctx.getPackageManager();
        if ("path".equals(op) && a.length > 1) {
            return pm.getApplicationInfo(a[1], 0).sourceDir;
        }
        boolean thirdParty = a.length > 1 && ("-3".equals(a[1]) || "--third-party".equals(a[1]));
        List<PackageInfo> list = pm.getInstalledPackages(0);
        StringBuilder sb = new StringBuilder();
        for (PackageInfo info : list) {
            ApplicationInfo app = info.applicationInfo;
            boolean system = app != null && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (thirdParty && system) {
                continue;
            }
            sb.append(info.packageName).append('\n');
        }
        return sb.toString();
    }

    private static String app(Context ctx, String[] a) throws Exception {
        String op = a.length > 0 ? a[0] : "details";
        String pkg = a.length > 1 ? a[1] : ctx.getPackageName();
        Intent intent;
        if ("uninstall".equals(op)) {
            intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + pkg));
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, false);
        } else if ("details".equals(op)) {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        } else if ("notifications".equals(op)) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
        } else if ("usage".equals(op)) {
            intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        } else {
            throw new IllegalArgumentException("usage: app <details|uninstall|notifications|usage> [package]");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
        return "ok";
    }

    private static void toast(Context ctx, String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                Toast.makeText(App.get(), text, 1).show();
            }
        });
    }

    private static String nullToEmpty(String str) {
        return str == null ? "" : str;
    }
}
