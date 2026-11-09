package ru.opencode.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public final class LocalServer {
    private static Thread acceptor;
    private static Context ctx;
    private static ServerSocket server;
    public static volatile boolean running = false;

    /* Тело запроса: 128 МБ с запасом на вложения. */
    private static final int MAX_BODY = 134217728;
    /* Заголовки: защита от мусора и медленных клиентов. */
    private static final int MAX_HEADER_LINES = 120;
    private static final int MAX_HEADER_CHARS = 65536;
    /* Прокси: обычный запрос не должен висеть дольше 5 минут (SSE — без лимита). */
    private static final int PROXY_READ_TIMEOUT_MS = 300000;
    /* Пул ограничен: SSE занимает поток надолго, поэтому очередь не нужна. */
    private static final ExecutorService pool = new ThreadPoolExecutor(
            4, 64, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
    /* Открытые локальные сайты: id → корневой каталог. id — capability-токен. */
    private static final ConcurrentHashMap<String, String> SITE_ROOTS = new ConcurrentHashMap<>();
    private static final SecureRandom SITE_RANDOM = new SecureRandom();
    private static final int SITE_ROOTS_MAX = 16;

    private LocalServer() {
    }

    public static synchronized void start(Context context) {
        if (running) {
            return;
        }
        ctx = context.getApplicationContext();
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("127.0.0.1", App.HTTP_PORT), 64);
            running = true;
            acceptor = new Thread(new Runnable() { // from class: ru.opencode.android.LocalServer.1
                @Override // java.lang.Runnable
                public void run() {
                    while (LocalServer.running) {
                        try {
                            final Socket socketAccept = LocalServer.server.accept();
                            try {
                                LocalServer.pool.execute(new Runnable() { // from class: ru.opencode.android.LocalServer.1.1
                                    @Override // java.lang.Runnable
                                    public void run() {
                                        LocalServer.handle(socketAccept);
                                    }
                                });
                            } catch (Throwable th0) {
                                // пул переполнен — не держим соединение
                                App.log("http: соединений слишком много, закрываю");
                                LocalServer.close(socketAccept);
                            }
                        } catch (Throwable th) {
                            if (LocalServer.running) {
                                App.log("accept: " + String.valueOf(th));
                            }
                        }
                    }
                }
            }, "local-http");
            acceptor.start();
            App.log("локальный сервер: http://127.0.0.1:8769");
        } catch (Throwable th) {
            running = false;
            App.log("не удалось запустить локальный сервер: " + String.valueOf(th));
        }
    }

    public static synchronized void stop() {
        running = false;
        try {
            if (server != null) {
                server.close();
            }
        } catch (Throwable th) {
        }
        server = null;
    }

    private static final class Req {
        byte[] body;
        Map<String, String> headers;
        String method;
        String path;
        String query;

        private Req() {
            this.headers = new LinkedHashMap();
            this.body = new byte[0];
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void handle(Socket socket) {
        int i;
        try {
            socket.setSoTimeout(120000);
            InputStream inputStream = socket.getInputStream();
            String line = readLine(inputStream);
            if (line != null && !line.isEmpty()) {
                String[] strArrSplit = line.split(" ");
                if (strArrSplit.length < 2) {
                    close(socket);
                    return;
                }
                Req req = new Req();
                int i2 = 0;
                req.method = strArrSplit[0].toUpperCase();
                String str = strArrSplit[1];
                int iIndexOf = str.indexOf(63);
                req.path = iIndexOf >= 0 ? str.substring(0, iIndexOf) : str;
                req.query = iIndexOf >= 0 ? str.substring(iIndexOf + 1) : "";
                int headerCount = 0;
                int headerChars = 0;
                while (true) {
                    String line2 = readLine(inputStream);
                    if (line2 == null || line2.isEmpty()) {
                        break;
                    }
                    headerCount++;
                    headerChars += line2.length();
                    if (headerCount > MAX_HEADER_LINES || headerChars > MAX_HEADER_CHARS) {
                        sendText(socket, 431, "text/plain", "too many headers");
                        return;
                    }
                    int iIndexOf2 = line2.indexOf(58);
                    if (iIndexOf2 > 0) {
                        req.headers.put(line2.substring(0, iIndexOf2).trim().toLowerCase(), line2.substring(iIndexOf2 + 1).trim());
                    }
                }
                // Токен проверяем ДО чтения тела: анонимный запрос не должен
                // занимать память даже заголовком Content-Length.
                if (needsToken(req.path) && !authorized(req)) {
                    sendText(socket, 403, "text/plain", "forbidden");
                    return;
                }
                String str2 = req.headers.get("transfer-encoding");
                if (str2 == null || !str2.toLowerCase().contains("chunked")) {
                    try {
                        i = Integer.parseInt(req.headers.getOrDefault("content-length", "0"));
                    } catch (Throwable th) {
                        i = 0;
                    }
                    if (i > MAX_BODY) {
                        sendText(socket, 413, "text/plain", "body too large");
                        return;
                    }
                    if (i > 0) {
                        byte[] bArr = new byte[i];
                        while (i2 < i) {
                            int i3 = inputStream.read(bArr, i2, i - i2);
                            if (i3 < 0) {
                                break;
                            } else {
                                i2 += i3;
                            }
                        }
                        req.body = bArr;
                    }
                } else {
                    try {
                        req.body = readChunked(inputStream, MAX_BODY);
                    } catch (Throwable tooBig) {
                        sendText(socket, 413, "text/plain", "body too large");
                        return;
                    }
                }
                route(socket, req);
                return;
            }
            close(socket);
        } catch (Throwable th2) {
            try {
                App.log("http: " + String.valueOf(th2));
                try {
                    sendText(socket, 500, "text/plain", "error: " + String.valueOf(th2));
                } catch (Throwable th3) {
                }
            } finally {
                close(socket);
            }
        }
    }

    /* Маршруты, которым нужен X-Bridge-Token. */
    private static boolean needsToken(String str) {
        return str.startsWith("/oc/") || str.startsWith("/android/") || str.equals("/upload")
                || str.equals("/upload_raw") || str.startsWith("/files/") || str.equals("/thumb")
                || str.equals("/site") || str.equals("/site/");
    }

    private static boolean authorized(Req req) {
        String str = req.headers.get("x-bridge-token");
        if (str == null || str.isEmpty()) {
            str = parseQuery(req.query).get("token");
        }
        return (str == null || str.isEmpty() || !str.equals(App.localToken)) ? false : true;
    }

    private static void route(Socket socket, Req req) throws Exception {
        String strSubstring = req.path;
        if (needsToken(strSubstring) && !authorized(req)) {
            sendText(socket, 403, "text/plain", "forbidden");
            return;
        }
        if (strSubstring.equals("/") || strSubstring.equals("/index.html")) {
            File file = new File(App.web, "page.html");
            if (!file.isFile()) {
                file = new File(App.web, "index.html");
            }
            sendFile(socket, file, "text/html; charset=utf-8");
            return;
        }
        if (strSubstring.equals("/ping")) {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("app", "opencode-remote");
            jSONObject.put("ver", 0);
            jSONObject.put("online", App.ocRunning);
            jSONObject.put("name", "Android");
            jSONObject.put("version", ctx == null ? "" : App.versionName(ctx));
            sendJson(socket, 200, jSONObject);
            return;
        }
        if (strSubstring.startsWith("/app/")) {
            File fileSafeChild = safeChild(App.web, strSubstring.substring(5));
            if (fileSafeChild == null || !fileSafeChild.isFile()) {
                sendText(socket, 404, "text/plain", "not found");
                return;
            } else {
                sendFile(socket, fileSafeChild, mime(fileSafeChild.getName()));
                return;
            }
        }
        if (strSubstring.equals("/site") || strSubstring.equals("/site/")
                || strSubstring.startsWith("/site/")) {
            site(socket, req);
            return;
        }
        if (strSubstring.startsWith("/oc/")) {
            proxy(socket, req, strSubstring.substring(3));
            return;
        }
        if (strSubstring.equals("/upload") || strSubstring.equals("/upload_raw")) {
            upload(socket, req);
            return;
        }
        if (strSubstring.startsWith("/files/")) {
            File fileResolveMedia = resolveMedia(URLDecoder.decode(strSubstring.substring(7), "UTF-8"));
            if (fileResolveMedia == null || !fileResolveMedia.isFile()) {
                sendText(socket, 404, "text/plain", "not found");
                return;
            } else {
                sendFile(socket, fileResolveMedia, mime(fileResolveMedia));
                return;
            }
        }
        if (strSubstring.equals("/thumb")) {
            thumb(socket, req);
            return;
        }
        if (strSubstring.startsWith("/android/")) {
            android(socket, req, strSubstring.substring(9));
            return;
        }
        File file2 = App.web;
        if (strSubstring.startsWith("/")) {
            strSubstring = strSubstring.substring(1);
        }
        File fileSafeChild2 = safeChild(file2, strSubstring);
        if (fileSafeChild2 != null && fileSafeChild2.isFile()) {
            sendFile(socket, fileSafeChild2, mime(fileSafeChild2.getName()));
        } else {
            sendText(socket, 404, "text/plain", "not found");
        }
    }

    private static void proxy(Socket socket, Req req, String str) {
        HttpURLConnection httpURLConnection = null;
        try {
            String replaceAll = req.query.replaceAll("(^|&)token=[^&]*", "").replaceAll("^&", "");
            httpURLConnection = (HttpURLConnection) new URL("http://127.0.0.1:" + App.ocPort + str + (replaceAll.isEmpty() ? "" : "?" + replaceAll)).openConnection();
            httpURLConnection.setRequestMethod(req.method);
            httpURLConnection.setConnectTimeout(10000);
            // SSE живёт бесконечно; обычные запросы не должны держать поток пула вечно.
            boolean sse = str.startsWith("/api/event");
            httpURLConnection.setReadTimeout(sse ? 0 : PROXY_READ_TIMEOUT_MS);
            httpURLConnection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(("opencode:" + App.password).getBytes(StandardCharsets.UTF_8), 2));
            String str2 = req.headers.get("content-type");
            if (str2 != null) {
                httpURLConnection.setRequestProperty("Content-Type", str2);
            }
            String str3 = req.headers.get("accept");
            if (str3 != null) {
                httpURLConnection.setRequestProperty("Accept", str3);
            }
            httpURLConnection.setRequestProperty("Accept-Encoding", "identity");
            if (req.body.length > 0) {
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setFixedLengthStreamingMode(req.body.length);
                OutputStream outputStream = httpURLConnection.getOutputStream();
                outputStream.write(req.body);
                outputStream.close();
            }
            int responseCode = httpURLConnection.getResponseCode();
            String contentType = httpURLConnection.getContentType();
            if (contentType == null) {
                contentType = "application/json; charset=utf-8";
            }
            OutputStream outputStream2 = socket.getOutputStream();
            if (contentType.toLowerCase().contains("text/event-stream")) {
                outputStream2.write(("HTTP/1.1 " + responseCode + " " + reason(responseCode) + "\r\nContent-Type: text/event-stream; charset=utf-8\r\nCache-Control: no-cache, no-store\r\nConnection: close\r\nX-Accel-Buffering: no\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                outputStream2.flush();
                InputStream errorStream = responseCode >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
                if (errorStream != null) {
                    byte[] bArr = new byte[8192];
                    while (true) {
                        int read = errorStream.read(bArr);
                        if (read <= 0) {
                            break;
                        }
                        outputStream2.write(bArr, 0, read);
                        outputStream2.flush();
                    }
                }
                return;
            }
            InputStream errorStream2 = responseCode >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            if (errorStream2 != null) {
                byte[] bArr2 = new byte[65536];
                while (true) {
                    int read2 = errorStream2.read(bArr2);
                    if (read2 <= 0) {
                        break;
                    }
                    byteArrayOutputStream.write(bArr2, 0, read2);
                }
            }
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            outputStream2.write(("HTTP/1.1 " + responseCode + " " + reason(responseCode) + "\r\nContent-Type: " + contentType + "\r\nContent-Length: " + byteArray.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream2.write(byteArray);
            outputStream2.flush();
        } catch (Throwable th) {
            try {
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("message", "OpenCode недоступен: " + th.getMessage());
                sendJson(socket, 502, jSONObject);
            } catch (Throwable th2) {
            }
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    /* Локальная сборка сайта: /site?path=<файл>&token=… → /site/<id>/<файл>.
       id случайный (capability), поэтому относительные css/js/img грузятся без токена. */
    private static void site(Socket socket, Req req) throws Exception {
        if (req.path.equals("/site") || req.path.equals("/site/")) {
            String target = parseQuery(req.query).get("path");
            if (target == null || target.trim().isEmpty()) {
                sendText(socket, 400, "text/plain", "path required");
                return;
            }
            File file = resolveMedia(target);
            if (file == null || !file.isFile()) {
                sendText(socket, 404, "text/plain", "site not found");
                return;
            }
            File dir = file.getParentFile();
            if (dir == null) {
                sendText(socket, 404, "text/plain", "site not found");
                return;
            }
            if (SITE_ROOTS.size() >= SITE_ROOTS_MAX) {
                SITE_ROOTS.clear();
            }
            byte[] rnd = new byte[16];
            SITE_RANDOM.nextBytes(rnd);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : rnd) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            String id = sb.toString();
            SITE_ROOTS.put(id, dir.getCanonicalPath());
            sendRedirect(socket, "/site/" + id + "/" + Uri.encode(file.getName()));
            return;
        }
        String rest;
        try {
            rest = URLDecoder.decode(req.path.substring("/site/".length()), "UTF-8");
        } catch (Throwable th) {
            rest = req.path.substring("/site/".length());
        }
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            sendText(socket, 404, "text/plain", "not found");
            return;
        }
        String root = SITE_ROOTS.get(rest.substring(0, slash));
        if (root == null) {
            sendText(socket, 404, "text/plain", "not found");
            return;
        }
        String rel = rest.substring(slash + 1);
        File base = new File(root);
        File target = new File(base, rel);
        String canonical = target.getCanonicalPath();
        if (!canonical.equals(root) && !canonical.startsWith(root + File.separator)) {
            sendText(socket, 404, "text/plain", "not found");
            return;
        }
        if (target.isDirectory()) {
            target = new File(target, "index.html");
        }
        if (!target.isFile()) {
            sendText(socket, 404, "text/plain", "not found");
            return;
        }
        sendFile(socket, target, mime(target));
    }

    private static void sendRedirect(Socket socket, String str) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(("HTTP/1.1 302 Found\r\nLocation: " + str
                + "\r\nContent-Length: 0\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private static String reason(int i) {
        switch (i) {
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 204:
                return "No Content";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 413:
                return "Payload Too Large";
            case 431:
                return "Request Header Fields Too Large";
            case 500:
                return "Internal Server Error";
            case 502:
                return "Bad Gateway";
            default:
                return "OK";
        }
    }

    private static void android(Socket socket, Req req, String str) throws Exception {
        Map<String, String> query = parseQuery(req.query);
        if (str.equals("fs")) {
            sendJson(socket, 200, fsJson(query.get("path")));
            return;
        }
        if (str.equals("mkdir")) {
            String str2 = query.get("parent");
            String str3 = query.get("name");
            JSONObject jSONObject = new JSONObject();
            if (str2 != null && str3 != null) {
                try {
                    if (!str3.isEmpty()) {
                        File file = new File(str2, str3);
                        if (!file.exists() && !file.mkdirs()) {
                            throw new Exception("не удалось создать");
                        }
                        jSONObject.put("ok", true);
                        jSONObject.put("path", file.getAbsolutePath());
                        sendJson(socket, 200, jSONObject);
                        return;
                    }
                } catch (Exception e) {
                    jSONObject.put("ok", false);
                    jSONObject.put("error", String.valueOf(e.getMessage()));
                }
            }
            throw new Exception("нет имени");
        }
        if (str.equals("occonfig")) {
            /* websearch.provider в ~/.config/opencode/opencode.json: единственный
               способ закрепить провайдера веб-поиска (API только читает список). */
            File file = new File(App.home, ".config/opencode/opencode.json");
            JSONObject out = new JSONObject();
            try {
                JSONObject conf = file.isFile() ? new JSONObject(readFile(file)) : new JSONObject();
                if ("POST".equals(req.method)) {
                    JSONObject body = new JSONObject(new String(req.body, StandardCharsets.UTF_8));
                    if (body.optBoolean("disabled", false)) {
                        conf.put("websearch", false);
                    } else if (body.has("provider")) {
                        String provider = body.optString("provider", "").trim();
                        if (provider.isEmpty() || "default".equals(provider)) {
                            conf.remove("websearch");
                        } else {
                            JSONObject ws = new JSONObject();
                            ws.put("provider", provider);
                            conf.put("websearch", ws);
                        }
                    } else {
                        throw new IllegalArgumentException("нет provider");
                    }
                    writeFileAtomic(file, conf.toString(2) + "\n");
                    App.log("occonfig: websearch=" + conf.opt("websearch"));
                }
                out.put("ok", true);
                Object ws = conf.opt("websearch");
                out.put("websearch", ws == null ? JSONObject.NULL : ws);
            } catch (Throwable th) {
                out.put("ok", false);
                out.put("error", String.valueOf(th.getMessage()));
            }
            sendJson(socket, out.optBoolean("ok") ? 200 : 400, out);
            return;
        }
        if (str.equals("home")) {
            String strDefaultDirectory = App.defaultDirectory();
            File file2 = new File(strDefaultDirectory);
            if (!file2.exists()) {
                file2.mkdirs();
            }
            JSONObject jSONObject2 = new JSONObject();
            jSONObject2.put("ok", true);
            jSONObject2.put("path", strDefaultDirectory);
            jSONObject2.put("allFiles", App.allFilesGranted());
            sendJson(socket, 200, jSONObject2);
            return;
        }
        if (str.equals("config")) {
            if ("POST".equals(req.method)) {
                try {
                    JSONObject jSONObject3 = new JSONObject(new String(req.body, StandardCharsets.UTF_8));
                    if (jSONObject3.has("workdir")) {
                        App.setDefaultDirectory(jSONObject3.getString("workdir"));
                    }
                    if (jSONObject3.has("notify")) {
                        App.prefs().edit().putBoolean("notify", jSONObject3.getBoolean("notify")).apply();
                    }
                    if (jSONObject3.has("sound")) {
                        App.prefs().edit().putBoolean("sound", jSONObject3.getBoolean("sound")).apply();
                    }
                    if (jSONObject3.has("vib")) {
                        App.prefs().edit().putBoolean("vib", jSONObject3.getBoolean("vib")).apply();
                    }
                } catch (Throwable th) {
                    App.log("config: " + String.valueOf(th));
                }
            }
            JSONObject jSONObject4 = new JSONObject();
            jSONObject4.put("workdir", App.defaultDirectory());
            jSONObject4.put("home", Environment.getExternalStorageDirectory().getAbsolutePath());
            jSONObject4.put("allFiles", App.allFilesGranted());
            jSONObject4.put("notify", App.prefs().getBoolean("notify", true));
            jSONObject4.put("sound", App.prefs().getBoolean("sound", false));
            jSONObject4.put("vib", App.prefs().getBoolean("vib", true));
            sendJson(socket, 200, jSONObject4);
            return;
        }
        if (str.equals("log")) {
            try {
                App.log("PAGE: " + new JSONObject(new String(req.body, StandardCharsets.UTF_8)).optString("msg"));
            } catch (Throwable th2) {
            }
            sendText(socket, 200, "text/plain", "ok");
            return;
        }
        if (str.equals("notify")) {
            try {
                JSONObject jSONObject5 = new JSONObject(new String(req.body, StandardCharsets.UTF_8));
                Notifications.show(ctx, jSONObject5.optString("title", App.TAG), jSONObject5.optString("text", ""));
            } catch (Throwable th3) {
                App.log("notify: " + String.valueOf(th3));
            }
            sendText(socket, 200, "text/plain", "ok");
            return;
        }
        if (str.equals("open")) {
            String str4 = query.get("path");
            if (str4 != null) {
                try {
                    Intent intent = new Intent("android.intent.action.VIEW");
                    intent.setDataAndType(Uri.fromFile(new File(str4)), mime(str4));
                    intent.addFlags(268435457);
                    ctx.startActivity(intent);
                } catch (Throwable th4) {
                    App.log("open: " + String.valueOf(th4));
                }
            }
            sendText(socket, 200, "text/plain", "ok");
            return;
        }
        sendText(socket, 404, "text/plain", "not found");
    }

    private static String readFile(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static void writeFileAtomic(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("не удалось создать " + parent);
        }
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            out.close();
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Throwable th) {
            if (!tmp.renameTo(file)) {
                throw new IOException("не удалось заменить " + file);
            }
        }
    }

    /* JADX WARN: Code duplicated, block: B:12:0x0027  */
    private static JSONObject fsJson(String str) {        JSONObject jSONObject = new JSONObject();
        try {
            boolean drives = str == null || str.isEmpty() || str.equals("::drives");
            File file = drives ? Environment.getExternalStorageDirectory() : new File(str);
            if (drives) {
                JSONArray entries = new JSONArray();
                entries.put(rootObj(Environment.getExternalStorageDirectory(), "Внутренняя память"));
                File[] roots = new File("/storage").listFiles();
                if (roots != null) {
                    for (File root : roots) {
                        String name = root.getName();
                        if (!name.equals("emulated") && !name.equals("self") && root.isDirectory()) {
                            entries.put(rootObj(root, name));
                        }
                    }
                }
                jSONObject.put("drives", true);
                jSONObject.put("path", "::drives");
                jSONObject.put("entries", entries);
                jSONObject.put("shortcuts", new JSONArray());
                jSONObject.put("ok", true);
                return jSONObject;
            }
            File[] listFiles = file.listFiles();
            Object parent = "";
            if (listFiles == null) {
                jSONObject.put("ok", false);
                Object error;
                if (file.exists()) {
                    error = "нет доступа к папке — разрешите доступ к файлам";
                } else {
                    error = "папка не найдена: " + file.getAbsolutePath();
                }
                jSONObject.put("error", error);
                jSONObject.put("path", file.getAbsolutePath());
                if (file.getParent() != null) {
                    parent = file.getParent();
                }
                jSONObject.put("parent", parent);
                jSONObject.put("entries", new JSONArray());
                jSONObject.put("shortcuts", shortcutsJson());
                return jSONObject;
            }
            ArrayList arrayList = new ArrayList();
            arrayList.addAll(Arrays.asList(listFiles));
            Collections.sort(arrayList, new Comparator() { // from class: ru.opencode.android.LocalServer$$ExternalSyntheticLambda0
                @Override // java.util.Comparator
                public final int compare(Object obj2, Object obj3) {
                    return LocalServer.lambda$fsJson$0((File) obj2, (File) obj3);
                }
            });
            JSONArray entries = new JSONArray();
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                File child = (File) it.next();
                if (!child.getName().startsWith(".")) {
                    JSONObject entry = new JSONObject();
                    entry.put("name", child.getName());
                    entry.put("path", child.getAbsolutePath());
                    entry.put("dir", child.isDirectory());
                    entry.put("img", !child.isDirectory() && isImage(child.getName()));
                    entry.put("size", child.isDirectory() ? "" : human(child.length()));
                    entries.put(entry);
                }
            }
            Object parent2 = file.getParent();
            if (parent2 != null) {
                parent = parent2;
            }
            jSONObject.put("ok", true);
            jSONObject.put("drives", false);
            jSONObject.put("path", file.getAbsolutePath());
            jSONObject.put("parent", parent);
            jSONObject.put("entries", entries);
            jSONObject.put("shortcuts", shortcutsJson());
            return jSONObject;
        } catch (Throwable th) {
            try {
                jSONObject.put("ok", false);
                jSONObject.put("error", String.valueOf(th.getMessage()));
                jSONObject.put("path", str);
                jSONObject.put("entries", new JSONArray());
                jSONObject.put("shortcuts", new JSONArray());
            } catch (Throwable th2) {
            }
            return jSONObject;
        }
    }

    static /* synthetic */ int lambda$fsJson$0(File file, File file2) {
        if (file.isDirectory() != file2.isDirectory()) {
            return file.isDirectory() ? -1 : 1;
        }
        return file.getName().compareToIgnoreCase(file2.getName());
    }

    private static JSONObject rootObj(File file, String str) throws Exception {
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("name", str);
        jSONObject.put("label", str);
        jSONObject.put("path", file.getAbsolutePath());
        jSONObject.put("dir", true);
        jSONObject.put("size", "");
        jSONObject.put("img", false);
        return jSONObject;
    }

    private static JSONArray shortcutsJson() throws Exception {
        JSONArray jSONArray = new JSONArray();
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        jSONArray.put(chip("Память", externalStorageDirectory));
        jSONArray.put(chip(App.TAG, new File(App.defaultDirectory())));
        jSONArray.put(chip("Загрузки", new File(externalStorageDirectory, "Download")));
        jSONArray.put(chip("Документы", new File(externalStorageDirectory, "Documents")));
        jSONArray.put(chip("Фото", new File(externalStorageDirectory, "Pictures")));
        return jSONArray;
    }

    private static JSONObject chip(String str, File file) throws Exception {
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("name", str);
        jSONObject.put("path", file.getAbsolutePath());
        return jSONObject;
    }

    private static File resolveMedia(String str) {
        try {
            File file = new File(str);
            if (file.isAbsolute() && file.isFile() && allowed(file)) {
                return file;
            }
        } catch (Throwable th) {
        }
        ArrayList arrayList = new ArrayList();
        arrayList.add(App.uploads);
        arrayList.add(new File(App.defaultDirectory()));
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        arrayList.add(new File(externalStorageDirectory, "Pictures"));
        arrayList.add(new File(externalStorageDirectory, "DCIM/Camera"));
        arrayList.add(new File(externalStorageDirectory, "Download"));
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            File file2 = new File((File) it.next(), str);
            if (file2.isFile()) {
                return file2;
            }
        }
        return null;
    }

    private static boolean allowed(File file) {
        try {
            String canonicalPath = file.getCanonicalPath();
            return canonicalPath.startsWith(Environment.getExternalStorageDirectory().getCanonicalPath()) || canonicalPath.startsWith(App.root.getCanonicalPath()) || canonicalPath.startsWith("/storage/");
        } catch (Throwable th) {
            return false;
        }
    }

    private static void sendFile(Socket socket, File file, String str) throws IOException {
        if (file == null || !file.isFile()) {
            sendText(socket, 404, "text/plain", "not found");
            return;
        }
        long length = file.length();
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(("HTTP/1.1 200 OK\r\nContent-Type: " + str + "\r\nContent-Length: " + length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] bArr = new byte[65536];
        while (true) {
            int i = fileInputStream.read(bArr);
            if (i <= 0) {
                fileInputStream.close();
                outputStream.flush();
                return;
            }
            outputStream.write(bArr, 0, i);
        }
    }

    private static void thumb(Socket socket, Req req) throws IOException {
        int i;
        Map<String, String> query = parseQuery(req.query);
        String str = query.get("path");
        try {
            i = Integer.parseInt(query.getOrDefault("w", "320"));
        } catch (Throwable th) {
            i = 320;
        }
        if (i < 64) {
            i = 64;
        }
        if (i > 1024) {
            i = 1024;
        }
        File file = str == null ? null : new File(str);
        if (file == null || !file.isFile() || !allowed(file)) {
            sendText(socket, 404, "text/plain", "not found");
            return;
        }
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            int i2 = 1;
            while (Math.min(options.outWidth, options.outHeight) / i2 > i * 2) {
                i2 *= 2;
            }
            BitmapFactory.Options options2 = new BitmapFactory.Options();
            options2.inSampleSize = i2;
            Bitmap bitmapDecodeFile = BitmapFactory.decodeFile(file.getAbsolutePath(), options2);
            if (bitmapDecodeFile == null) {
                sendText(socket, 404, "text/plain", "not image");
                return;
            }
            int width = (bitmapDecodeFile.getWidth() - Math.min(bitmapDecodeFile.getWidth(), bitmapDecodeFile.getHeight())) / 2;
            int height = (bitmapDecodeFile.getHeight() - Math.min(bitmapDecodeFile.getWidth(), bitmapDecodeFile.getHeight())) / 2;
            int iMin = Math.min(bitmapDecodeFile.getWidth(), bitmapDecodeFile.getHeight());
            Bitmap bitmapCreateScaledBitmap = Bitmap.createScaledBitmap(Bitmap.createBitmap(bitmapDecodeFile, width, height, iMin, iMin), i, i, true);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmapCreateScaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: " + byteArray.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(byteArray);
            outputStream.flush();
        } catch (Throwable th2) {
            sendText(socket, 500, "text/plain", String.valueOf(th2));
        }
    }

    private static void upload(Socket socket, Req req) throws Exception {
        JSONObject jSONObject = new JSONObject();
        JSONArray jSONArray = new JSONArray();
        try {
            String orDefault = req.headers.getOrDefault("content-type", "");
            if (orDefault.toLowerCase().contains("multipart/form-data")) {
                String strTrim = null;
                for (String str : orDefault.split(";")) {
                    String strTrim2 = str.trim();
                    if (strTrim2.toLowerCase().startsWith("boundary=")) {
                        strTrim = strTrim2.substring(9).trim();
                        if (strTrim.startsWith("\"") && strTrim.endsWith("\"")) {
                            strTrim = strTrim.substring(1, strTrim.length() - 1);
                        }
                    }
                }
                if (strTrim == null) {
                    throw new Exception("нет boundary");
                }
                byte[] bytes = ("--" + strTrim).getBytes(StandardCharsets.ISO_8859_1);
                byte[] crlfcrlf = {13, 10, 13, 10};
                int i = 0;
                while (true) {
                    int boundary = indexOf(req.body, bytes, i);
                    if (boundary < 0) {
                        break;
                    }
                    int p = boundary + bytes.length;
                    if (p + 2 <= req.body.length && req.body[p] == 45 && req.body[p + 1] == 45) {
                        break;
                    }
                    if (p + 2 <= req.body.length && req.body[p] == 13 && req.body[p + 1] == 10) {
                        p += 2;
                    }
                    int headerEnd = indexOf(req.body, crlfcrlf, p);
                    if (headerEnd < 0) {
                        break;
                    }
                    String headers = new String(req.body, p, headerEnd - p, StandardCharsets.UTF_8);
                    int dataStart = headerEnd + 4;
                    int next = indexOf(req.body, bytes, dataStart);
                    if (next < 0) {
                        next = req.body.length;
                    }
                    int dataEnd = next;
                    while (dataEnd > dataStart && (req.body[dataEnd - 1] == 13 || req.body[dataEnd - 1] == 10)) {
                        dataEnd--;
                    }
                    i = next;
                    String name = "file";
                    for (String header : headers.split("\r\n")) {
                        int fileIndex = header.toLowerCase().indexOf("filename=");
                        if (header.toLowerCase().startsWith("content-disposition:") && fileIndex >= 0) {
                            name = header.substring(fileIndex + 9).trim();
                            if (name.startsWith("\"") && name.endsWith("\"")) {
                                name = name.substring(1, name.length() - 1);
                            }
                        }
                    }
                    File fileUniqueUpload = uniqueUpload(name);
                    FileOutputStream fileOutputStream = new FileOutputStream(fileUniqueUpload);
                    fileOutputStream.write(req.body, dataStart, Math.max(0, dataEnd - dataStart));
                    fileOutputStream.close();
                    jSONArray.put(fileObj(fileUniqueUpload));
                }
            } else {
                File fileUniqueUpload2 = uniqueUpload(parseQuery(req.query).getOrDefault("name", "file"));
                FileOutputStream fileOutputStream2 = new FileOutputStream(fileUniqueUpload2);
                fileOutputStream2.write(req.body);
                fileOutputStream2.close();
                jSONArray.put(fileObj(fileUniqueUpload2));
            }
            jSONObject.put("ok", true);
            jSONObject.put("files", jSONArray);
        } catch (Throwable th) {
            jSONObject.put("ok", false);
            jSONObject.put("error", String.valueOf(th.getMessage()));
        }
        sendJson(socket, 200, jSONObject);
    }

    private static JSONObject fileObj(File file) throws Exception {
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("path", file.getAbsolutePath());
        jSONObject.put("name", file.getName());
        jSONObject.put("size", file.length());
        return jSONObject;
    }

    private static File uniqueUpload(String str) {
        if (str == null || str.isEmpty()) {
            str = "file";
        }
        String strReplace = str.replace('/', '_').replace('\\', '_');
        File file = new File(App.uploads, strReplace);
        if (!file.exists()) {
            return file;
        }
        int iLastIndexOf = strReplace.lastIndexOf(46);
        String strSubstring = iLastIndexOf > 0 ? strReplace.substring(0, iLastIndexOf) : strReplace;
        String strSubstring2 = iLastIndexOf > 0 ? strReplace.substring(iLastIndexOf) : "";
        for (int i = 2; i < 10000; i++) {
            File file2 = new File(App.uploads, strSubstring + "-" + i + strSubstring2);
            if (!file2.exists()) {
                return file2;
            }
        }
        return new File(App.uploads, System.currentTimeMillis() + "-" + strReplace);
    }

    private static int indexOf(byte[] bArr, byte[] bArr2, int i) {
        for (int iMax = Math.max(0, i); iMax <= bArr.length - bArr2.length; iMax++) {
            boolean found = true;
            for (int i2 = 0; i2 < bArr2.length; i2++) {
                if (bArr[iMax + i2] != bArr2[i2]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return iMax;
            }
        }
        return -1;
    }

    private static Map<String, String> parseQuery(String str) {
        HashMap map = new HashMap();
        if (str == null) {
            return map;
        }
        for (String str2 : str.split("&")) {
            int iIndexOf = str2.indexOf(61);
            try {
                if (iIndexOf < 0) {
                    map.put(URLDecoder.decode(str2, "UTF-8"), "");
                } else {
                    map.put(URLDecoder.decode(str2.substring(0, iIndexOf), "UTF-8"), URLDecoder.decode(str2.substring(iIndexOf + 1), "UTF-8"));
                }
            } catch (Throwable th) {
            }
        }
        return map;
    }

    private static String readLine(InputStream inputStream) throws IOException {
        int i;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (true) {
            i = inputStream.read();
            if (i < 0 || i == 10) {
                break;
            }
            if (i != 13) {
                byteArrayOutputStream.write(i);
            }
        }
        if (i >= 0 || byteArrayOutputStream.size() != 0) {
            return byteArrayOutputStream.toString("UTF-8");
        }
        return null;
    }

    private static byte[] readChunked(InputStream inputStream, int max) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (true) {
            String line = readLine(inputStream);
            if (line == null) {
                break;
            }
            int iIndexOf = line.indexOf(59);
            if (iIndexOf >= 0) {
                line = line.substring(0, iIndexOf);
            }
            int i;
            try {
                i = Integer.parseInt(line.trim(), 16);
            } catch (Throwable th) {
                continue;
            }
            if (i == 0) {
                readLine(inputStream);
                break;
            }
            if (i < 0 || byteArrayOutputStream.size() + i > max) {
                throw new IOException("body too large");
            }
            byte[] bArr = new byte[i];
            int i2 = 0;
            while (i2 < i) {
                int i3 = inputStream.read(bArr, i2, i - i2);
                if (i3 < 0) {
                    break;
                }
                i2 += i3;
            }
            byteArrayOutputStream.write(bArr, 0, i2);
            readLine(inputStream);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static void sendJson(Socket socket, int i, JSONObject jSONObject) throws IOException {
        sendText(socket, i, "application/json; charset=utf-8", jSONObject.toString());
    }

    private static void sendText(Socket socket, int i, String str, String str2) throws IOException {
        byte[] bytes = str2.getBytes(StandardCharsets.UTF_8);
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(("HTTP/1.1 " + i + " " + reason(i) + "\r\nContent-Type: " + str + "\r\nContent-Length: " + bytes.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(bytes);
        outputStream.flush();
    }

    private static void close(Socket socket) {
        try {
            socket.close();
        } catch (Throwable th) {
        }
    }

    private static File safeChild(File file, String str) {
        try {
            File file2 = new File(file, str);
            if (file2.getCanonicalPath().startsWith(file.getCanonicalPath())) {
                return file2;
            }
            return null;
        } catch (Throwable th) {
            return null;
        }
    }

    private static boolean isImage(String str) {
        String lowerCase = str.toLowerCase();
        return lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg") || lowerCase.endsWith(".png") || lowerCase.endsWith(".webp") || lowerCase.endsWith(".gif") || lowerCase.endsWith(".bmp");
    }

    private static String human(long j) {
        if (j < 1024) {
            return j + " Б";
        }
        if (j < 1048576) {
            return (j / 1024) + " КБ";
        }
        return j < 1073741824 ? String.format(Locale.US, "%.1f МБ", Double.valueOf(j / 1048576.0d)) : String.format(Locale.US, "%.1f ГБ", Double.valueOf(j / 1.073741824E9d));
    }

    /* Файлы генерации часто без расширения — определяем по сигнатуре. */
    private static String mime(File file) {
        String byName = mime(file.getName());
        if (!"application/octet-stream".equals(byName)) {
            return byName;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] b = new byte[12];
            int n = in.read(b);
            if (n >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
                return "image/png";
            }
            if (n >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            if (n >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
                return "image/gif";
            }
            if (n >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
                return "image/webp";
            }
            if (n >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
                return "video/mp4";
            }
            if (n >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45
                    && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) {
                return "video/webm";
            }
            if (n >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') {
                return "audio/mpeg";
            }
            if (n >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
                return "application/pdf";
            }
        } catch (Throwable th) {
        }
        return byName;
    }

    private static String mime(String str) {
        String lowerCase = str.toLowerCase();
        if (lowerCase.endsWith(".html") || lowerCase.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        }
        if (lowerCase.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (lowerCase.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lowerCase.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (lowerCase.endsWith(".png")) {
            return "image/png";
        }
        if (lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerCase.endsWith(".webp")) {
            return "image/webp";
        }
        if (lowerCase.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerCase.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lowerCase.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lowerCase.endsWith(".webm")) {
            return "video/webm";
        }
        if (lowerCase.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lowerCase.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerCase.endsWith(".txt") || lowerCase.endsWith(".md")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
