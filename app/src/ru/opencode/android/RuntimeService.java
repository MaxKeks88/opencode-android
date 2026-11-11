package ru.opencode.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public class RuntimeService extends Service {
    public static final String ACTION_STOP = "ru.opencode.android.STOP";
    private static final String CHANNEL = "opencode_bg";
    private static final int NOTIF_ID = 1;
    private static volatile RuntimeService INSTANCE;
    private Thread notifier;
    private Process proc;
    private Thread reader;
    private volatile boolean started = false;
    private volatile boolean stopping = false;
    private volatile boolean foregroundActive = false;
    private volatile boolean bootHold = false;
    private volatile int busyCount = 0;
    private Thread worker;
    private final ScheduledExecutorService notifierScheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Long> pendingStamp = new ConcurrentHashMap<String, Long>();
    private final Map<String, String> pendingType = new ConcurrentHashMap<String, String>();
    private final Set<String> askedIds = Collections.synchronizedSet(new HashSet<String>());

    public static void start(Context context) {
        context.startForegroundService(new Intent(context, (Class<?>) RuntimeService.class));
    }

    /* После перезагрузки держим foreground, пока пользователь не откроет приложение. */
    public static void startOnBoot(Context context) {
        context.startForegroundService(new Intent(context, (Class<?>) RuntimeService.class).putExtra("boot", true));
    }

    public static void refreshForeground(Context context) {
        /* Совместимость: сервис в том же процессе, обновляемся напрямую. */
        uiChanged();
    }

    /* MainActivity сообщает о видимости — сразу пересчитываем foreground. */
    public static void uiChanged() {
        RuntimeService s = INSTANCE;
        if (s != null) {
            s.updateForeground();
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, (Class<?>) RuntimeService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        createChannel();
        /* startForegroundService требует foreground за 5 секунд — заявляем сразу,
           а через мгновение прячем, если приложение на экране и задач нет. */
        startForeground(NOTIF_ID, notification("Запуск OpenCode…"));
        foregroundActive = true;
        try {
            notifierScheduler.schedule(new Runnable() {
                public void run() {
                    updateForeground();
                }
            }, 1500L, TimeUnit.MILLISECONDS);
            notifierScheduler.scheduleWithFixedDelay(new Runnable() {
                public void run() {
                    updateForeground();
                }
            }, 20L, 20L, TimeUnit.SECONDS);
        } catch (Throwable th) {
        }
    }

    /* Уведомление сервиса нужно только когда задача реально идёт в фоне. */
    private void updateForeground() {
        try {
            if (MainActivity.visible) {
                bootHold = false;
            }
            boolean need = bootHold || (!MainActivity.visible && busyCount > 0);
            if (need) {
                if (!foregroundActive) {
                    startForeground(NOTIF_ID, notification("OpenCode работает…"));
                    foregroundActive = true;
                }
            } else if (foregroundActive) {
                stopForeground(true);
                foregroundActive = false;
            }
        } catch (Throwable th) {
            App.log("fgs: " + String.valueOf(th));
        }
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        if (intent != null && intent.getBooleanExtra("boot", false)) {
            bootHold = true;
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            this.stopping = true;
            stopEverything();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!this.started) {
            this.started = true;
            this.worker = new Thread(new Runnable() { // from class: ru.opencode.android.RuntimeService.1
                @Override // java.lang.Runnable
                public void run() {
                    RuntimeService.this.loop();
                }
            }, "oc-supervisor");
            this.worker.start();
        }
        return START_STICKY;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code duplicated, block: B:30:0x00b9  */
    /* JADX WARN: Code duplicated, block: B:31:0x00d1  */
    public void loop() {
        killOrphanPid();
        killOrphans();
        int failures = 0;
        while (!this.stopping) {
            try {
                update("Подготовка…");
                Runtime.ensure(this);
                ProxyServer.start(this);
                if (!App.ocRunning) {
                    update("Запуск OpenCode…");
                    startOpencode();
                }
                if (!waitInfo(90000L)) {
                    throw new RuntimeException("OpenCode не ответил за 90 секунд");
                }
                App.ocRunning = true;
                App.lastError = "";
                update("OpenCode работает");
                if (!LocalServer.running) {
                    LocalServer.start(this);
                }
                App.serverRunning = true;
                if (this.notifier == null) {
                    this.notifier = new Thread(new Runnable() { // from class: ru.opencode.android.RuntimeService.2
                        @Override // java.lang.Runnable
                        public void run() {
                            RuntimeService.this.notifyLoop();
                        }
                    }, "oc-notify");
                    this.notifier.start();
                }
                failures = 0;
                int code = this.proc.waitFor();
                App.ocRunning = false;
                App.log("opencode завершился с кодом " + code);
            } catch (Throwable th) {
                App.ocRunning = false;
                failures++;
                App.lastError = String.valueOf(th);
                App.log("ошибка рантайма: " + String.valueOf(th));
                update(failures > 2 ? "Ошибка: " + String.valueOf(th) : "Перезапуск…");
                try {
                    Thread.sleep(Math.min(15000, failures * 2000));
                } catch (InterruptedException e) {
                }
            }
            if (this.stopping) {
                return;
            }
            update("OpenCode перезапускается…");
            try {
                Thread.sleep(2500L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void startOpencode() throws Exception {
        File fileLoader = App.loader();
        File fileOpencode = App.opencode();
        if (!fileLoader.isFile() || !fileOpencode.isFile()) {
            throw new RuntimeException("нет файлов рантайма");
        }
        ArrayList arrayList = new ArrayList();
        arrayList.add(fileLoader.getAbsolutePath());
        arrayList.add("--library-path");
        arrayList.add(App.lib.getAbsolutePath());
        arrayList.add(fileOpencode.getAbsolutePath());
        arrayList.add("serve");
        arrayList.add("--hostname");
        arrayList.add("127.0.0.1");
        App.ocPort = freePort();
        App.log("порт OpenCode: " + App.ocPort);
        arrayList.add("--port");
        arrayList.add(String.valueOf(App.ocPort));
        arrayList.add("--print-logs");
        ProcessBuilder processBuilder = new ProcessBuilder(arrayList);
        processBuilder.directory(App.home);
        Map<String, String> mapEnvironment = processBuilder.environment();
        mapEnvironment.put("HOME", App.home.getAbsolutePath());
        mapEnvironment.put("XDG_DATA_HOME", new File(App.home, ".local/share").getAbsolutePath());
        mapEnvironment.put("XDG_CONFIG_HOME", new File(App.home, ".config").getAbsolutePath());
        mapEnvironment.put("XDG_CACHE_HOME", new File(App.home, ".cache").getAbsolutePath());
        mapEnvironment.put("XDG_STATE_HOME", new File(App.home, ".local/state").getAbsolutePath());
        mapEnvironment.put("TMPDIR", App.tmp.getAbsolutePath());
        mapEnvironment.put("TMP", App.tmp.getAbsolutePath());
        mapEnvironment.put("TEMP", App.tmp.getAbsolutePath());
        mapEnvironment.put("PATH", App.bin.getAbsolutePath() + ":/system/bin:/system/xbin:/vendor/bin");
        mapEnvironment.put("SHELL", "/system/bin/sh");
        mapEnvironment.put("LANG", "en_US.UTF-8");
        mapEnvironment.put("LC_ALL", "en_US.UTF-8");
        mapEnvironment.put("TERM", "dumb");
        mapEnvironment.put("NO_COLOR", "1");
        mapEnvironment.put("OPENCODE_PASSWORD", App.password);
        mapEnvironment.put("OPENCODE_DISABLE_FFF", "1");
        if (ProxyServer.port > 0) {
            String str = "http://127.0.0.1:" + ProxyServer.port;
            mapEnvironment.put("HTTP_PROXY", str);
            mapEnvironment.put("HTTPS_PROXY", str);
            mapEnvironment.put("http_proxy", str);
            mapEnvironment.put("https_proxy", str);
            mapEnvironment.put("ALL_PROXY", str);
            mapEnvironment.put("all_proxy", str);
            mapEnvironment.put("NO_PROXY", "127.0.0.1,localhost");
            mapEnvironment.put("no_proxy", "127.0.0.1,localhost");
            App.log("opencode через прокси: " + str);
        }
        processBuilder.redirectErrorStream(true);
        this.proc = processBuilder.start();
        int iPidOf = pidOf(this.proc);
        App.log("opencode запущен pid=" + iPidOf);
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(App.root, "opencode.pid"));
            fileOutputStream.write(String.valueOf(iPidOf).getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
        } catch (Throwable th) {
        }
        final InputStream inputStream = this.proc.getInputStream();
        this.reader = new Thread(new Runnable() { // from class: ru.opencode.android.RuntimeService.3
            @Override // java.lang.Runnable
            public void run() {
                try {
                    File file = new File(App.logs, "opencode.log");
                    if (file.length() > 8388608) {
                        File file2 = new File(App.logs, "opencode.old.log");
                        if (file2.exists()) {
                            file2.delete();
                        }
                        file.renameTo(file2);
                    }
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8);
                    while (true) {
                        String line = bufferedReader.readLine();
                        if (line != null) {
                            outputStreamWriter.write(line);
                            outputStreamWriter.write(10);
                            outputStreamWriter.flush();
                        } else {
                            outputStreamWriter.close();
                            return;
                        }
                    }
                } catch (Throwable th2) {
                }
            }
        }, "oc-log");
        this.reader.start();
    }

    private boolean waitInfo(long j) {
        long jCurrentTimeMillis = System.currentTimeMillis() + j;
        String str = "Basic " + Base64.encodeToString(("opencode:" + App.password).getBytes(StandardCharsets.UTF_8), 2);
        while (System.currentTimeMillis() < jCurrentTimeMillis && !this.stopping) {
            HttpURLConnection httpURLConnection = null;
            try {
                HttpURLConnection httpURLConnection2 = (HttpURLConnection) new URL("http://127.0.0.1:" + App.ocPort + "/api/info").openConnection();
                try {
                    httpURLConnection2.setRequestProperty("Authorization", str);
                    httpURLConnection2.setConnectTimeout(2500);
                    httpURLConnection2.setReadTimeout(4000);
                    if (httpURLConnection2.getResponseCode() == 200) {
                        if (httpURLConnection2 != null) {
                            httpURLConnection2.disconnect();
                            return true;
                        }
                        return true;
                    }
                    if (httpURLConnection2 != null) {
                        httpURLConnection2.disconnect();
                    }
                    try {
                        Thread.sleep(700L);
                    } catch (InterruptedException e) {
                    }
                } catch (Throwable th) {
                    httpURLConnection = httpURLConnection2;
                    if (httpURLConnection != null) {
                        httpURLConnection.disconnect();
                    }
                }
            } catch (Throwable th2) {
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code duplicated, block: B:104:0x01da A[PHI: r6
  0x01da: PHI (r6v2 java.net.HttpURLConnection) = (r6v1 java.net.HttpURLConnection), (r6v9 java.net.HttpURLConnection) binds: [B:103:0x01d8, B:89:0x01a9] A[DONT_GENERATE, DONT_INLINE]] */
    /* JADX WARN: Code duplicated, block: B:108:0x01e2  */
    /* JADX WARN: Code duplicated, block: B:118:0x01d3 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:137:0x01eb A[SYNTHETIC] */
    public void notifyLoop() {
        HashSet hashSet = new HashSet();
        try {
            /* Точный список активных сессий: time.busy > time.idle бывает
               «залипшим» после падения провайдера. */
            JSONObject active = getJson("/api/session/active", 12000);
            JSONObject activeData = active == null ? null : active.optJSONObject("data");
            if (activeData != null) {
                java.util.Iterator<String> it = activeData.keys();
                while (it.hasNext()) {
                    String sid = it.next();
                    if (sid != null && !sid.isEmpty()) {
                        hashSet.add(sid);
                    }
                }
            }
        } catch (Throwable th) {
        }
        if (hashSet.isEmpty()) {
            try {
                JSONObject json = getJson("/api/session?limit=20&order=desc", 12000);
                JSONArray jSONArrayOptJSONArray = json == null ? null : json.optJSONArray("data");
                if (jSONArrayOptJSONArray != null) {
                    for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                        JSONObject session = jSONArrayOptJSONArray.optJSONObject(i);
                        JSONObject time;
                        if (session != null && session.optString("outcome", "").isEmpty()
                                && (time = session.optJSONObject("time")) != null
                                && time.optLong("busy", 0L) > time.optLong("idle", 0L)) {
                            hashSet.add(session.optString("id"));
                        }
                    }
                }
            } catch (Throwable th) {
            }
        }
        busyCount = hashSet.size();
        while (!this.stopping) {
            HttpURLConnection httpURLConnection = null;
            BufferedReader bufferedReader = null;
            try {
                httpURLConnection = (HttpURLConnection) new URL("http://127.0.0.1:" + App.ocPort + "/api/event").openConnection();
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setReadTimeout(0);
                httpURLConnection.setRequestProperty("Accept", "text/event-stream");
                httpURLConnection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(("opencode:" + App.password).getBytes(StandardCharsets.UTF_8), 2));
                if (httpURLConnection.getResponseCode() != 200) {
                    throw new RuntimeException("SSE " + httpURLConnection.getResponseCode());
                }
                bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (!this.stopping && (line = bufferedReader.readLine()) != null) {
                    try {
                        if (line.startsWith("data:")) {
                            String strTrim = line.substring(5).trim();
                            if (!strTrim.isEmpty()) {
                                JSONObject jSONObject = new JSONObject(strTrim);
                                String strOptString = jSONObject.optString("type");
                                JSONObject data = jSONObject.optJSONObject("data");
                                String strOptString2 = data == null ? "" : data.optString("sessionID");
                                long jCurrentTimeMillis = System.currentTimeMillis();
                                if (strOptString.equals("session.execution.started")) {
                                    if (!strOptString2.isEmpty()) {
                                        hashSet.add(strOptString2);
                                        busyCount = hashSet.size();
                                        updateForeground();
                                        pendingStamp.remove(strOptString2);
                                        pendingType.remove(strOptString2);
                                    }
                                } else if (strOptString.equals("session.execution.succeeded") || strOptString.equals("session.execution.failed") || strOptString.equals("session.execution.interrupted")) {
                                    if (!strOptString2.isEmpty()) {
                                        hashSet.remove(strOptString2);
                                        busyCount = hashSet.size();
                                        updateForeground();
                                        scheduleCheck(strOptString2, strOptString);
                                    }
                                } else if (strOptString.equals("permission.asked") || strOptString.equals("form.created")) {
                                    String key = data == null ? "" : data.optString("id", "");
                                    if (key.isEmpty() && data != null) {
                                        key = data.optString("permissionID", "");
                                    }
                                    if (key.isEmpty() && data != null) {
                                        key = data.optString("formID", "");
                                    }
                                    if (key.isEmpty()) {
                                        key = strOptString2 + ":" + strOptString;
                                    }
                                    if (!askedIds.contains(key)) {
                                        askedIds.add(key);
                                        if (!MainActivity.visible && App.prefs().getBoolean("notify", true)
                                                && jCurrentTimeMillis - Notifications.lastShownAt > 2000) {
                                            /* Запрос требует ответа, но не должен вибрировать на каждом шаге. */
                                            Notifications.showQuiet(this, App.TAG, "OpenCode ждёт вашего ответа");
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable th2) {
                    }
                }
            } catch (Throwable th) {
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (Throwable th3) {
                    }
                }
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            }
            if (!this.stopping) {
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private void scheduleCheck(final String sid, String type) {
        pendingType.put(sid, type);
        final long stamp = System.currentTimeMillis();
        pendingStamp.put(sid, Long.valueOf(stamp));
        try {
            notifierScheduler.schedule(new Runnable() {
                public void run() {
                    checkDone(sid, stamp, 0, false);
                }
            }, 2600L, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
        }
    }

    /** Проверяем, что сессия реально свободна, и показываем одно уведомление. */
    private void checkDone(final String sid, final long stamp, final int attempt, final boolean confirmed) {
        Long cur = pendingStamp.get(sid);
        if (cur == null || cur.longValue() != stamp) {
            return;
        }
        if (this.stopping || MainActivity.visible || !App.prefs().getBoolean("notify", true)) {
            pendingStamp.remove(sid, Long.valueOf(stamp));
            pendingType.remove(sid);
            return;
        }
        boolean done = false;
        boolean idleMsg = false;
        try {
            JSONObject j = getJson("/api/session/" + sid, 8000);
            JSONObject d = j == null ? null : j.optJSONObject("data");
            if (d == null) {
                d = j;
            }
            JSONObject t = d == null ? null : d.optJSONObject("time");
            if (t != null) {
                long b = t.optLong("busy", 0L);
                long i = t.optLong("idle", 0L);
                done = i > 0L && i >= b;
            }
        } catch (Throwable ignored) {
        }
        /* Полный ход заканчивается служебным сообщением type:"idle". */
        try {
            JSONObject mj = getJson("/api/session/" + sid + "/message?limit=1", 8000);
            JSONArray arr = mj == null ? null : mj.optJSONArray("data");
            if (arr != null && arr.length() > 0) {
                JSONObject mm = arr.optJSONObject(0);
                idleMsg = mm != null && "idle".equals(mm.optString("type"));
            }
        } catch (Throwable ignored) {
        }
        if (!done || !idleMsg) {
            if (attempt < 6) {
                try {
                    notifierScheduler.schedule(new Runnable() {
                        public void run() {
                            checkDone(sid, stamp, attempt + 1, false);
                        }
                    }, 1500L, TimeUnit.MILLISECONDS);
                } catch (Throwable ignored) {
                }
            } else {
                pendingStamp.remove(sid, Long.valueOf(stamp));
                pendingType.remove(sid);
            }
            return;
        }
        /* Ещё одна пауза: execution.* приходит на каждый шаг, не будим телефон зря. */
        if (!confirmed) {
            try {
                notifierScheduler.schedule(new Runnable() {
                    public void run() {
                        checkDone(sid, stamp, attempt, true);
                    }
                }, 1400L, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
            }
            return;
        }
        pendingStamp.remove(sid, Long.valueOf(stamp));
        String type = pendingType.remove(sid);
        if (type == null) {
            return;
        }
        if (System.currentTimeMillis() - Notifications.lastShownAt < 1500L) {
            return;
        }
        Notifications.show(this, App.TAG, type.endsWith("failed") ? "Ошибка выполнения" : "Ответ готов");
    }

    private JSONObject getJson(String str, int i) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL("http://127.0.0.1:" + App.ocPort + str).openConnection();
            httpURLConnection.setConnectTimeout(4000);
            httpURLConnection.setReadTimeout(i);
            httpURLConnection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(("opencode:" + App.password).getBytes(StandardCharsets.UTF_8), 2));
            if (httpURLConnection.getResponseCode() == 200) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
                bufferedReader.close();
                if (sb.length() != 0) {
                    return new JSONObject(sb.toString());
                }
            }
            return null;
        } catch (Throwable th) {
            return null;
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    private int freePort() {
        for (int port = 4096; port < 4196; port++) {
            ServerSocket serverSocket = null;
            try {
                ServerSocket serverSocket2 = new ServerSocket();
                try {
                    serverSocket2.setReuseAddress(true);
                    serverSocket2.bind(new InetSocketAddress("127.0.0.1", port));
                    try {
                        serverSocket2.close();
                    } catch (Throwable th) {
                    }
                    return port;
                } catch (Throwable th2) {
                    serverSocket = serverSocket2;
                    if (serverSocket != null) {
                        try {
                            serverSocket.close();
                        } catch (Throwable th3) {
                        }
                    }
                }
            } catch (Throwable th4) {
            }
        }
        App.log("freePort: 4096–4195 заняты, пробуем " + App.OC_PORT);
        return App.OC_PORT;
    }

    private int pidOf(Process process) {
        try {
            Field declaredField = process.getClass().getDeclaredField("pid");
            declaredField.setAccessible(true);
            return declaredField.getInt(process);
        } catch (Throwable th) {
            return -1;
        }
    }

    private void killOrphanPid() {
        File file = new File(App.root, "opencode.pid");
        if (file.isFile()) {
            try {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                String line = bufferedReader.readLine();
                bufferedReader.close();
                int i = Integer.parseInt(line == null ? "" : line.trim());
                if (i > 0 && i != android.os.Process.myPid()) {
                    App.log("убиваю opencode из pid-файла: " + i);
                    android.os.Process.killProcess(i);
                }
            } catch (Throwable th) {
                App.log("killOrphanPid: " + String.valueOf(th));
            }
            file.delete();
            try {
                Thread.sleep(400L);
            } catch (InterruptedException e) {
            }
        }
    }

    private void killOrphans() {
        BufferedReader bufferedReader = null;
        try {
            Process start = new ProcessBuilder("sh", "-c", "ps -A -o PID,ARGS").redirectErrorStream(true).start();
            bufferedReader = new BufferedReader(new InputStreamReader(start.getInputStream(), StandardCharsets.UTF_8));
            String readLine;
            while ((readLine = bufferedReader.readLine()) != null) {
                if (readLine.contains("/files/usr/bin/opencode")) {
                    String[] split = readLine.trim().split("\\s+", 2);
                    if (split.length >= 2) {
                        try {
                            int parseInt = Integer.parseInt(split[0]);
                            if (parseInt != android.os.Process.myPid()) {
                                App.log("убиваю старый opencode pid=" + parseInt);
                                android.os.Process.killProcess(parseInt);
                            }
                        } catch (Throwable th3) {
                        }
                    }
                }
            }
            start.waitFor();
            Thread.sleep(500L);
        } catch (Throwable th) {
            App.log("killOrphans: " + String.valueOf(th));
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Throwable th4) {
                }
            }
        }
    }

    private void stopEverything() {
        this.stopping = true;
        try {
            notifierScheduler.shutdownNow();
        } catch (Throwable ignored) {
        }
        try {
            if (this.proc != null) {
                this.proc.destroy();
            }
        } catch (Throwable th) {
        }
        killOrphans();
        try {
            new File(App.root, "opencode.pid").delete();
        } catch (Throwable th2) {
        }
        try {
            LocalServer.stop();
        } catch (Throwable th3) {
        }
        try {
            ProxyServer.stop();
        } catch (Throwable th4) {
        }
        App.ocRunning = false;
        App.serverRunning = false;
    }

    @Override // android.app.Service
    public void onDestroy() {
        if (INSTANCE == this) {
            INSTANCE = null;
        }
        stopEverything();
        super.onDestroy();
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationManager notificationManager = (NotificationManager) getSystemService("notification");
        /* Канал без важности: foreground-сервис обязан иметь уведомление,
           но в шторке оно не показывается и не мешает. */
        NotificationChannel notificationChannel = new NotificationChannel(
                CHANNEL, App.TAG, NotificationManager.IMPORTANCE_NONE);
        notificationChannel.setShowBadge(false);
        notificationChannel.setSound(null, null);
        notificationChannel.enableVibration(false);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        notificationManager.createNotificationChannel(notificationChannel);
        try {
            notificationManager.deleteNotificationChannel("opencode");
        } catch (Throwable th) {
        }
    }

    private Notification notification(String str) {
        Intent intent = new Intent(this, (Class<?>) MainActivity.class);
        intent.setFlags(536870912);
        PendingIntent activity = PendingIntent.getActivity(this, 0, intent, 201326592);
        Intent intent2 = new Intent(this, (Class<?>) RuntimeService.class);
        intent2.setAction(ACTION_STOP);
        PendingIntent service = PendingIntent.getService(this, NOTIF_ID, intent2, 201326592);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL);
        builder.setContentTitle(App.TAG).setContentText(Lang.tr(str)).setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false).setShowWhen(false).setContentIntent(activity)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, Lang.t("Стоп", "Stop"), service).build());
        return builder.build();
    }

    private void update(String str) {
        /* Статус сервиса не показываем: уведомление только когда задача идёт в фоне. */
        updateForeground();
    }
}
