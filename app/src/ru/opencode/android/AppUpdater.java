package ru.opencode.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Обновление самого приложения через GitHub Releases.
 * Релиз: тег vX.Y.Z, ассет *.apk. Проверяем версию, SHA-256 (digest) и подпись,
 * затем отдаём APK системному PackageInstaller.
 */
public final class AppUpdater {
    /** Публичный репозиторий с релизами приложения. */
    public static final String REPO = "MaxKeks88/opencode-android";
    public static final String RELEASE_URL = "https://github.com/" + REPO + "/releases/latest";

    private static final String CACHE = "app_update_cache";
    private static final String AUTO_PREF = "app_update_auto";
    private static final long AUTO_INTERVAL_MS = 6 * 3600 * 1000L;
    private static final int NOTIF_ID = 1002;
    private static final String CHANNEL = "updates";
    private static final Object LOCK = new Object();

    private static volatile boolean busy = false;
    private static volatile String lastError = "";

    public interface Callback {
        void done(String text);
    }

    private AppUpdater() {
    }

    public static String currentVersion(Context context) {
        return App.versionName(context);
    }

    public static boolean autoEnabled() {
        try {
            return App.prefs().getBoolean(AUTO_PREF, true);
        } catch (Throwable th) {
            return true;
        }
    }

    public static void setAutoEnabled(boolean enabled) {
        try {
            App.prefs().edit().putBoolean(AUTO_PREF, enabled).apply();
        } catch (Throwable th) {
        }
    }

    /** Текущее состояние для страницы: кэш релиза + флаги. */
    public static JSONObject state(Context context) {
        JSONObject out = new JSONObject();
        try {
            JSONObject data = readCache();
            String current = currentVersion(context);
            String tag = data.optString("tag", "");
            out.put("ok", true);
            out.put("current", current);
            out.put("version", data.optString("version", ""));
            out.put("tag", tag);
            out.put("url", data.optString("url", ""));
            out.put("size", data.optLong("size", 0));
            out.put("notes", data.optString("notes", ""));
            out.put("releaseUrl", data.optString("releaseUrl", RELEASE_URL));
            out.put("checkedAt", data.optLong("checkedAt", 0));
            out.put("available", !tag.isEmpty() && Updater.isNewer(tag, current));
            out.put("checking", busy);
            out.put("installing", busy);
            out.put("error", lastError);
            out.put("auto", autoEnabled());
        } catch (Throwable th) {
            try {
                out.put("ok", false);
                out.put("error", String.valueOf(th));
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static JSONObject readCache() {
        try {
            String raw = App.prefs().getString(CACHE, "");
            return raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (Throwable th) {
            return new JSONObject();
        }
    }

    /** Синхронно спрашивает последний релиз (без prerelease/draft) и кэширует. */
    public static JSONObject checkSync(Context context) throws Exception {
        JSONObject release = new JSONObject(get("https://api.github.com/repos/" + REPO + "/releases/latest"));
        if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
            throw new Exception("последний релиз помечен как черновик/предрелиз");
        }
        String tag = release.optString("tag_name", "").trim();
        if (tag.isEmpty()) {
            throw new Exception("у релиза нет тега");
        }
        JSONObject asset = findAsset(release.optJSONArray("assets"));
        if (asset == null) {
            throw new Exception("в релизе нет APK-ассета");
        }
        String url = asset.optString("browser_download_url", "");
        if (url.isEmpty()) {
            throw new Exception("у ассета нет ссылки");
        }
        JSONObject data = new JSONObject();
        data.put("tag", tag);
        data.put("version", tag.replaceFirst("^[vV]", ""));
        data.put("url", url);
        data.put("size", asset.optLong("size", 0));
        data.put("digest", asset.optString("digest", ""));
        data.put("asset", asset.optString("name", ""));
        data.put("notes", release.optString("body", ""));
        data.put("releaseUrl", release.optString("html_url", RELEASE_URL));
        data.put("checkedAt", System.currentTimeMillis());
        App.prefs().edit().putString(CACHE, data.toString()).apply();
        App.log("app-update: последний релиз " + tag + ", доступно="
                + Updater.isNewer(tag, currentVersion(context)));
        return data;
    }

    private static JSONObject findAsset(JSONArray assets) {
        if (assets == null) {
            return null;
        }
        JSONObject fallback = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            String name = asset.optString("name", "");
            if (!name.toLowerCase().endsWith(".apk")) {
                continue;
            }
            if (name.toLowerCase().contains("opencodeandroid")) {
                return asset;
            }
            if (fallback == null) {
                fallback = asset;
            }
        }
        return fallback;
    }

    /** Проверка в фоне; force=false учитывает 6-часовой кэш. */
    public static void checkAsync(final Context context, final boolean force, final Callback callback) {
        synchronized (LOCK) {
            if (busy) {
                if (callback != null) {
                    callback.done("Проверка уже идёт");
                }
                return;
            }
            if (!force) {
                JSONObject cached = readCache();
                long checkedAt = cached.optLong("checkedAt", 0);
                if (checkedAt > 0 && System.currentTimeMillis() - checkedAt < AUTO_INTERVAL_MS) {
                    if (callback != null) {
                        callback.done(state(context).optBoolean("available")
                                ? "Доступно обновление " + cached.optString("version") : "Обновлений нет");
                    }
                    return;
                }
            }
            busy = true;
            lastError = "";
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text;
                try {
                    JSONObject data;
                    try {
                        data = checkSync(context);
                    } catch (Throwable first) {
                        // мобильная сеть/DNS иногда просыпается с задержкой — одна повторная попытка
                        App.log("app-update: повтор после " + first);
                        Thread.sleep(2500L);
                        data = checkSync(context);
                    }
                    text = Updater.isNewer(data.optString("tag"), currentVersion(context))
                            ? "Доступно обновление " + data.optString("version")
                            : "Установлена последняя версия " + currentVersion(context);
                } catch (Throwable th) {
                    lastError = "Не удалось проверить обновления: " + th.getMessage();
                    App.log("app-update: " + th);
                    text = lastError;
                } finally {
                    synchronized (LOCK) {
                        busy = false;
                    }
                }
                if (callback != null) {
                    callback.done(text);
                }
            }
        }, "app-update-check").start();
    }

    /**
     * Скачивает и запускает установку. Возвращает состояние сразу;
     * результат приходит в callback (и системным установщиком).
     */
    public static void installAsync(final Context context, final Callback callback) {
        synchronized (LOCK) {
            if (busy) {
                if (callback != null) {
                    callback.done("Уже идёт проверка или загрузка");
                }
                return;
            }
            busy = true;
            lastError = "";
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text;
                try {
                    if (!allowedToInstall(context)) {
                        openInstallSettings(context);
                        text = "Разрешите установку из этого источника и нажмите «Скачать» ещё раз";
                        lastError = text;
                    } else {
                        JSONObject data = readCache();
                        if (!Updater.isNewer(data.optString("tag"), currentVersion(context))) {
                            text = "Обновлений нет";
                        } else {
                            File apk = downloadApk(context, data);
                            text = installApk(context, apk);
                        }
                    }
                } catch (Throwable th) {
                    text = "Ошибка обновления: " + th.getMessage();
                    lastError = text;
                    App.log("app-update: " + th);
                } finally {
                    cancelProgress(context);
                    synchronized (LOCK) {
                        busy = false;
                    }
                }
                if (callback != null) {
                    callback.done(text);
                }
            }
        }, "app-update-download").start();
    }

    private static File downloadApk(Context context, JSONObject data) throws Exception {
        String url = data.optString("url", "");
        if (url.isEmpty()) {
            throw new Exception("нет ссылки на APK");
        }
        File dir = new File(App.root, "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("не удалось создать " + dir);
        }
        File apk = new File(dir, "OpenCodeAndroid-" + data.optString("version", "update") + ".apk");
        File part = new File(dir, "update.apk.part");
        try {
            download(context, url, part, data.optLong("size", 0), data.optString("version", ""));
            String digest = data.optString("digest", "");
            if (digest.startsWith("sha256:")) {
                String actual = sha256(part);
                if (!actual.equalsIgnoreCase(digest.substring(7))) {
                    throw new Exception("контрольная сумма не совпала");
                }
                App.log("app-update: sha256 совпал");
            } else {
                App.log("app-update: digest в релизе нет — проверяю подпись и версию");
            }
            verifyPackage(context, part);
            if (apk.exists() && !apk.delete()) {
                throw new Exception("не удалось заменить старый APK");
            }
            if (!part.renameTo(apk)) {
                throw new Exception("не удалось переименовать APK");
            }
            return apk;
        } catch (Throwable th) {
            part.delete();
            throw th;
        }
    }

    private static void verifyPackage(Context context, File apk) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (archive == null) {
            throw new Exception("скачанный файл не APK");
        }
        if (!context.getPackageName().equals(archive.packageName)) {
            throw new Exception("чужой пакет: " + archive.packageName);
        }
        long archiveCode = versionCode(archive);
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), 0);
        if (archiveCode <= versionCode(installed)) {
            throw new Exception("версия в APK не новее установленной");
        }
        if (!sameSignature(pm, archive)) {
            throw new Exception("APK подписан другим ключом — установка поверх невозможна");
        }
    }

    private static boolean sameSignature(PackageManager pm, PackageInfo archive) {
        try {
            PackageInfo installed = pm.getPackageInfo(archive.packageName, PackageManager.GET_SIGNATURES);
            Signature[] a = installed.signatures;
            Signature[] b = archive.signatures;
            if (a == null || b == null || a.length == 0 || b.length == 0) {
                return false;
            }
            return signHash(a[0]).equals(signHash(b[0]));
        } catch (Throwable th) {
            App.log("app-update: подпись не проверена: " + th);
            return false;
        }
    }

    private static String signHash(Signature signature) throws Exception {        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(signature.toByteArray());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static long versionCode(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) {
            return info.getLongVersionCode();
        }
        return info.versionCode;
    }

    /** Отдаёт APK системному установщику через content:// (диалог подтверждения). */
    private static String installApk(final Context context, File apk) throws Exception {
        final Uri uri = Uri.parse("content://" + UpdateProvider.AUTHORITY + "/" + Uri.encode(apk.getName()));
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    context.startActivity(intent);
                } catch (Throwable th) {
                    App.log("app-update: установщик: " + th);
                }
            }
        });
        App.log("app-update: установка " + apk.getName() + " передана системе");
        return "Скачано. Подтвердите установку в системном окне.";
    }

    public static boolean allowedToInstall(Context context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                return context.getPackageManager().canRequestPackageInstalls();
            }
            return Settings.Secure.getInt(context.getContentResolver(),
                    "install_non_market_apps", 1) == 1;
        } catch (Throwable th) {
            return true;
        }
    }

    public static void openInstallSettings(final Context context) {
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Throwable th) {
                        App.log("app-update: настройки установки: " + th);
                    }
                }
            });
        } catch (Throwable th) {
            App.log("app-update: настройки установки: " + th);
        }
    }

    private static void download(Context context, String url, File target, long total, String version)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(120000);
        connection.setRequestProperty("User-Agent", "OpenCodeAndroid");
        connection.setInstanceFollowRedirects(true);
        int code = connection.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code);
        }
        long expected = total > 0 ? total : connection.getContentLengthLong();
        InputStream in = connection.getInputStream();
        FileOutputStream out = new FileOutputStream(target);
        long done = 0;
        int lastPct = -1;
        try {
            byte[] buf = new byte[1048576];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
                done += read;
                if (expected > 0) {
                    int pct = (int) (done * 100 / expected);
                    if (pct != lastPct) {
                        lastPct = pct;
                        notifyProgress(context, pct, version);
                    }
                }
            }
            out.flush();
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
            out.close();
            connection.disconnect();
        }
        if (expected > 0 && done != expected) {
            throw new IOException("скачано " + done + " из " + expected + " байт");
        }
        if (done < 50L * 1024 * 1024) {
            throw new IOException("файл слишком мал: " + done);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[65536];
            int read;
            while ((read = in.read(buf)) > 0) {
                digest.update(buf, 0, read);
            }
        } finally {
            in.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static void notifyProgress(Context context, int percent, String version) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService("notification");
            if (nm == null) {
                return;
            }
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Обновления приложения", 2));
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pending = PendingIntent.getActivity(context, 20, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = new Notification.Builder(context, CHANNEL)
                    .setContentTitle("Обновление OpenCode" + (version.isEmpty() ? "" : " " + version))
                    .setContentText("Скачивание: " + percent + "%")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setProgress(100, percent, false)
                    .setOngoing(true)
                    .setContentIntent(pending);
            nm.notify(NOTIF_ID, builder.build());
        } catch (Throwable th) {
            App.log("app-update: уведомление прогресса: " + th);
        }
    }

    private static void cancelProgress(Context context) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService("notification");
            if (nm != null) {
                nm.cancel(NOTIF_ID);
            }
        } catch (Throwable th) {
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "OpenCodeAndroid");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        int code = connection.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code);
        }
        InputStream in = connection.getInputStream();
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
            connection.disconnect();
        }
    }
}
