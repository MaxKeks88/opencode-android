package ru.opencode.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/* loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public class MainActivity extends Activity {
    private static final int REQ_FILE_CHOOSER = 103;
    private static final int REQ_NOTIFY = 101;
    private static final int REQ_PICK = 102;
    private static final int REQ_STORAGE = 100;
    private static final int TEXT_ZOOM = 100;
    public static volatile boolean visible = false;
    private ProgressBar bar;
    private TextView detail;
    private ValueCallback<Uri[]> fileCallback;
    private volatile boolean loaded = false;
    private volatile boolean ready = false;
    private FrameLayout root;
    private LinearLayout splash;
    private TextView status;
    private WebView web;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (!App.prefs().getBoolean("onboarded", false)) {
            startActivity(new Intent(this, (Class<?>) OnboardingActivity.class));
            finish();
            return;
        }
        getWindow().setStatusBarColor(-16052460);
        getWindow().setNavigationBarColor(-16052460);
        this.root = new FrameLayout(this);
        this.root.setBackgroundColor(-16052460);
        this.web = new WebView(this);
        this.web.setBackgroundColor(-16052460);
        this.web.setVisibility(4);
        WebSettings settings = this.web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(0);
        settings.setCacheMode(2);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setTextZoom(TEXT_ZOOM);
        settings.setSafeBrowsingEnabled(false);
        try {
            // Отладка WebView только в debuggable-сборке: в релизе CDP не нужен.
            if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        } catch (Throwable th) {
        }
        this.web.addJavascriptInterface(new AndroidBridge(this), "Android");
        this.web.setWebViewClient(new WebViewClient() { // from class: ru.opencode.android.MainActivity.1
            @Override // android.webkit.WebViewClient
            public boolean shouldOverrideUrlLoading(WebView webView, String str) {
                if (MainActivity.this.isAppUrl(str)) {
                    return false;
                }
                MainActivity.this.openExternally(str);
                return true;
            }

            @Override // android.webkit.WebViewClient
            public void onPageFinished(WebView webView, String str) {
                MainActivity.this.loaded = true;
                if (MainActivity.this.ready) {
                    MainActivity.this.showWeb();
                }
            }
        });
        this.web.setWebChromeClient(new WebChromeClient() { // from class: ru.opencode.android.MainActivity.2
            @Override // android.webkit.WebChromeClient
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                App.log("JS " + consoleMessage.message() + " (" + consoleMessage.lineNumber() + ")");
                return true;
            }

            @Override // android.webkit.WebChromeClient
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> valueCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (MainActivity.this.fileCallback != null) {
                    MainActivity.this.fileCallback.onReceiveValue(null);
                }
                MainActivity.this.fileCallback = valueCallback;
                try {
                    MainActivity.this.startActivityForResult(Intent.createChooser(fileChooserParams.createIntent(), "Выбрать файл"), MainActivity.REQ_FILE_CHOOSER);
                    return true;
                } catch (Throwable th2) {
                    App.log("file chooser: " + String.valueOf(th2));
                    MainActivity.this.fileCallback = null;
                    return false;
                }
            }
        });
        this.splash = new LinearLayout(this);
        this.splash.setOrientation(1);
        this.splash.setPadding(dp(26), dp(40), dp(26), dp(26));
        this.splash.setBackgroundColor(-16052460);
        this.splash.setGravity(1);
        TextView textView = new TextView(this);
        textView.setText("⌨️");
        textView.setTextSize(46.0f);
        textView.setGravity(17);
        this.splash.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText(App.TAG + " " + App.versionName(this));
        textView2.setTextSize(24.0f);
        textView2.setTextColor(-1);
        textView2.setGravity(17);
        textView2.setPadding(0, dp(8), 0, dp(6));
        this.splash.addView(textView2);
        this.status = new TextView(this);
        this.status.setText("Запуск…");
        this.status.setTextColor(-7626568);
        this.status.setTextSize(14.0f);
        this.status.setGravity(17);
        this.splash.addView(this.status);
        this.bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        this.bar.setIndeterminate(true);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(3));
        layoutParams.topMargin = dp(18);
        this.bar.setLayoutParams(layoutParams);
        this.splash.addView(this.bar);
        this.detail = new TextView(this);
        this.detail.setText("");
        this.detail.setTextColor(-10520429);
        this.detail.setTextSize(12.0f);
        this.detail.setPadding(0, dp(16), 0, 0);
        this.detail.setGravity(17);
        this.splash.addView(this.detail);
        Button button = new Button(this);
        button.setText("Настройки");
        button.setTextColor(-1);
        button.getBackground().setAlpha(60);
        button.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.MainActivity.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) SettingsActivity.class));
            }
        });
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams2.topMargin = dp(22);
        button.setLayoutParams(layoutParams2);
        this.splash.addView(button);
        this.root.addView(this.web, new FrameLayout.LayoutParams(-1, -1));
        this.root.addView(this.splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(this.root);
        requestPermissionsIfNeeded();
        RuntimeService.start(this);
        waitAndLoad();
    }

    private void waitAndLoad() {
        new Thread(new Runnable() { // from class: ru.opencode.android.MainActivity.4
            @Override // java.lang.Runnable
            public void run() {
                long currentTimeMillis = System.currentTimeMillis() + 180000;
                while (System.currentTimeMillis() < currentTimeMillis) {
                    if (MainActivity.this.ping()) {
                        MainActivity.this.ready = true;
                        MainActivity.this.runOnUiThread(new Runnable() { // from class: ru.opencode.android.MainActivity.4.1
                            @Override // java.lang.Runnable
                            public void run() {
                                MainActivity.this.status.setText("Открываю чат…");
                                MainActivity.this.web.loadUrl("http://127.0.0.1:8769/?v=" + System.currentTimeMillis() + "#token=" + App.localToken);
                            }
                        });
                        return;
                    } else {
                        MainActivity.this.runOnUiThread(new Runnable() { // from class: ru.opencode.android.MainActivity.4.2
                            @Override // java.lang.Runnable
                            public void run() {
                                if (!App.lastError.isEmpty()) {
                                    MainActivity.this.status.setText("Не удалось запустить");
                                    MainActivity.this.detail.setText(App.lastError);
                                } else {
                                    MainActivity.this.status.setText("Запуск OpenCode… первый раз может занять до минуты");
                                }
                            }
                        });
                        try {
                            Thread.sleep(700L);
                        } catch (InterruptedException e) {
                        }
                    }
                }
                MainActivity.this.runOnUiThread(new Runnable() { // from class: ru.opencode.android.MainActivity.4.3
                    @Override // java.lang.Runnable
                    public void run() {
                        MainActivity.this.status.setText("OpenCode не запустился");
                        MainActivity.this.detail.setText(App.lastError.isEmpty() ? "Откройте настройки и посмотрите журнал" : App.lastError);
                    }
                });
            }
        }, "wait-http").start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean ping() {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL("http://127.0.0.1:8769/ping").openConnection();
            httpURLConnection.setConnectTimeout(1200);
            httpURLConnection.setReadTimeout(1500);
            return httpURLConnection.getResponseCode() == 200;
        } catch (Throwable th) {
            return false;
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showWeb() {
        if (this.loaded) {
            this.splash.setVisibility(8);
            this.web.setVisibility(0);
        }
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
        }
        ArrayList arrayList = new ArrayList();
        if (checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != 0) {
            arrayList.add("android.permission.READ_EXTERNAL_STORAGE");
        }
        if (Build.VERSION.SDK_INT <= 32 && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != 0) {
            arrayList.add("android.permission.WRITE_EXTERNAL_STORAGE");
        }
        if (!arrayList.isEmpty()) {
            requestPermissions((String[]) arrayList.toArray(new String[0]), REQ_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager() && !App.prefs().getBoolean("asked_all_files", false)) {
            App.prefs().edit().putBoolean("asked_all_files", true).apply();
            new AlertDialog.Builder(this).setTitle("Доступ к файлам").setMessage("OpenCode нужно читать и изменять файлы на телефоне. Разрешите «Доступ ко всем файлам» для этого приложения.").setPositiveButton("Открыть настройки", new DialogInterface.OnClickListener() { // from class: ru.opencode.android.MainActivity$$ExternalSyntheticLambda0
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m14xc6b51846(dialogInterface, i);
                }
            }).setNegativeButton("Позже", (DialogInterface.OnClickListener) null).show();
        }
    }

    /* renamed from: lambda$requestPermissionsIfNeeded$0$ru-opencode-android-MainActivity, reason: not valid java name */
    /* synthetic */ void m14xc6b51846(DialogInterface dialogInterface, int i) {
        openAllFilesSettings();
    }

    public void openAllFilesSettings() {
        try {
            startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", Uri.parse("package:" + getPackageName())));
        } catch (Throwable th) {
            try {
                startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"));
            } catch (Throwable th2) {
            }
        }
    }

    @Override // android.app.Activity
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i, strArr, iArr);
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == REQ_FILE_CHOOSER) {
            if (this.fileCallback != null) {
                try {
                    this.fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(i2, intent));
                } catch (Throwable th) {
                    this.fileCallback.onReceiveValue(null);
                }
                this.fileCallback = null;
                return;
            }
            return;
        }
        if (i == REQ_PICK && i2 == -1 && intent != null && intent.getData() != null) {
            final Uri data = intent.getData();
            new Thread(new Runnable() { // from class: ru.opencode.android.MainActivity.5
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        String str = "file";
                        Cursor query = MainActivity.this.getContentResolver().query(data, null, null, null, null);
                        if (query != null) {
                            int columnIndex = query.getColumnIndex("_display_name");
                            if (columnIndex >= 0 && query.moveToFirst()) {
                                str = query.getString(columnIndex);
                            }
                            query.close();
                        }
                        File file = new File(App.uploads, System.currentTimeMillis() + "-" + str);
                        InputStream openInputStream = MainActivity.this.getContentResolver().openInputStream(data);
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        byte[] bArr = new byte[65536];
                        while (true) {
                            int read = openInputStream.read(bArr);
                            if (read <= 0) {
                                openInputStream.close();
                                fileOutputStream.close();
                                final String absolutePath = file.getAbsolutePath();
                                final String fileName = str;
                                MainActivity.this.runOnUiThread(new Runnable() { // from class: ru.opencode.android.MainActivity.5.1
                                    @Override // java.lang.Runnable
                                    public void run() {
                                        MainActivity.this.web.evaluateJavascript("window.__ocPicked && window.__ocPicked(" + MainActivity.js(absolutePath) + "," + MainActivity.js(fileName) + ")", null);
                                    }
                                });
                                return;
                            }
                            fileOutputStream.write(bArr, 0, read);
                        }
                    } catch (Throwable th2) {
                        App.log("pick: " + String.valueOf(th2));
                    }
                }
            }).start();
        }
    }

    static String js(String str) {
        if (str == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            switch (charAt) {
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    sb.append(charAt);
                    break;
            }
        }
        return sb.append('\"').toString();
    }

    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public boolean onKeyDown(int i, KeyEvent keyEvent) {
        if (i == 4) {
            if (this.web.canGoBack()) {
                this.web.goBack();
                return true;
            }
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(i, keyEvent);
    }

    private int dp(int i) {
        return (int) (i * getResources().getDisplayMetrics().density);
    }

    /* Свой адрес: только http://127.0.0.1:8769 (иначе — во внешний браузер). */
    private boolean isAppUrl(String str) {
        try {
            Uri uri = Uri.parse(str);
            return "http".equalsIgnoreCase(uri.getScheme())
                    && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                    && uri.getPort() == App.HTTP_PORT;
        } catch (Throwable th) {
            return false;
        }
    }

    /* Внешние ссылки — в браузер; file:/javascript:/data: не открываем вовсе. */
    private void openExternally(String str) {
        try {
            Uri uri = Uri.parse(str);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return;
            }
            scheme = scheme.toLowerCase();
            if (scheme.equals("file") || scheme.equals("javascript") || scheme.equals("data")
                    || scheme.equals("about") || scheme.equals("blob") || scheme.equals("chrome")) {
                App.log("nav blocked: " + str);
                return;
            }
            startActivity(new Intent("android.intent.action.VIEW", uri));
        } catch (Throwable th) {
            App.log("nav: " + String.valueOf(th));
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        visible = true;
        try {
            if (App.prefs().getBoolean("keep_screen_on", false)) {
                getWindow().addFlags(128);
            } else {
                getWindow().clearFlags(128);
            }
        } catch (Throwable th) {
        }
    }

    @Override // android.app.Activity
    protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        visible = false;
        try {
            this.web.destroy();
        } catch (Throwable th) {
        }
        super.onDestroy();
    }
}
