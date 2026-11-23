package ru.opencode.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
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
    private long splashStartedAt = 0;
    private boolean splashLeaving = false;
    private SplashLogoView splashLogo;
    private FrameLayout root;
    private LinearLayout splash;
    private TextView status;
    private WebView web;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (!App.prefs().getBoolean("onboarded", false)) {
            String lang = App.prefs().getString(Lang.PREF, "");
            Class<?> next = (lang == null || lang.isEmpty()) ? LanguageActivity.class : OnboardingActivity.class;
            startActivity(new Intent(this, next));
            finish();
            return;
        }
        getWindow().setStatusBarColor(-16052460);
        getWindow().setNavigationBarColor(-16052460);
        this.splashStartedAt = System.currentTimeMillis();
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
                    MainActivity.this.startActivityForResult(Intent.createChooser(fileChooserParams.createIntent(), Lang.tr("Выбрать файл")), MainActivity.REQ_FILE_CHOOSER);
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
        SplashLogoView logo = new SplashLogoView(this);
        this.splashLogo = logo;
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(-1, dp(230));
        logo.setLayoutParams(logoParams);
        this.splash.addView(logo);
        TextView textView2 = new TextView(this);
        textView2.setText(App.TAG + " " + App.versionName(this));
        textView2.setTextSize(24.0f);
        textView2.setTextColor(-1);
        textView2.setGravity(17);
        textView2.setPadding(0, dp(8), 0, dp(6));
        this.splash.addView(textView2);
        this.status = new TextView(this);
        this.status.setText(Lang.tr("Запуск…"));
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
        button.setText(Lang.tr("Настройки"));
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
        if (AppUpdater.autoEnabled()) {
            AppUpdater.checkAsync(this, false, null);
        }
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
                                MainActivity.this.status.setText(Lang.tr("Открываю чат…"));
                                MainActivity.this.web.loadUrl("http://127.0.0.1:8769/?v=" + System.currentTimeMillis() + "#token=" + App.localToken);
                            }
                        });
                        return;
                    } else {
                        MainActivity.this.runOnUiThread(new Runnable() { // from class: ru.opencode.android.MainActivity.4.2
                            @Override // java.lang.Runnable
                            public void run() {
                                if (!App.lastError.isEmpty()) {
                                    MainActivity.this.status.setText(Lang.tr("Не удалось запустить"));
                                    MainActivity.this.detail.setText(App.lastError);
                                } else {
                                    MainActivity.this.status.setText(Lang.tr("Запуск OpenCode… первый раз может занять до минуты"));
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
                        MainActivity.this.status.setText(Lang.tr("OpenCode не запустился"));
                        MainActivity.this.detail.setText(App.lastError.isEmpty() ? Lang.tr("Откройте настройки и посмотрите журнал") : Lang.tr(App.lastError));
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
        if (!this.loaded || this.splashLeaving) {
            return;
        }
        this.splashLeaving = true;
        long wait = Math.max(0L, 1100L - (System.currentTimeMillis() - this.splashStartedAt));
        this.web.setVisibility(0);
        this.web.setAlpha(0.0f);
        this.splash.postDelayed(new Runnable() { // from class: ru.opencode.android.MainActivity.5
            @Override // java.lang.Runnable
            public void run() {
                MainActivity.this.web.animate().alpha(1.0f).setDuration(430L).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).start();
                MainActivity.this.splash.animate().alpha(0.0f).scaleX(1.045f).scaleY(1.045f)
                        .setDuration(380L)
                        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                        .withEndAction(new Runnable() { // from class: ru.opencode.android.MainActivity.5.1
                            @Override // java.lang.Runnable
                            public void run() {
                                MainActivity.this.splash.setVisibility(8);
                                if (MainActivity.this.splashLogo != null) {
                                    MainActivity.this.splashLogo.stop();
                                }
                            }
                        }).start();
            }
        }, wait);
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
            new AlertDialog.Builder(this).setTitle(Lang.tr("Доступ к файлам")).setMessage(Lang.tr("OpenCode нужно читать и изменять файлы на телефоне. Разрешите «Доступ ко всем файлам» для этого приложения.")).setPositiveButton(Lang.tr("Открыть настройки"), new DialogInterface.OnClickListener() { // from class: ru.opencode.android.MainActivity$$ExternalSyntheticLambda0
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m14xc6b51846(dialogInterface, i);
                }
            }).setNegativeButton(Lang.tr("Позже"), (DialogInterface.OnClickListener) null).show();
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
        RuntimeService.refreshForeground(this);
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
        RuntimeService.refreshForeground(this);
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

    /* Анимированный экран загрузки: свечение, кольца, «>_» и бегущий блик. */
    private final class SplashLogoView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clip = new Path();
        private final float density;
        private final ValueAnimator anim;
        private float t = 0.0f;

        SplashLogoView(Context context) {
            super(context);
            this.density = getResources().getDisplayMetrics().density;
            this.textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            this.textPaint.setTextAlign(Paint.Align.CENTER);
            this.textPaint.setColor(0xFFE8EEF6);
            this.textPaint.setTextSize(44.0f * this.density);
            this.anim = ValueAnimator.ofFloat(0.0f, 1.0f);
            this.anim.setDuration(2400L);
            this.anim.setRepeatCount(ValueAnimator.INFINITE);
            this.anim.setInterpolator(new LinearInterpolator());
            this.anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    t = ((Float) valueAnimator.getAnimatedValue()).floatValue();
                    invalidate();
                }
            });
            this.anim.start();
        }

        @Override // android.view.View
        protected void onDetachedFromWindow() {
            this.anim.cancel();
            super.onDetachedFromWindow();
        }

        void stop() {
            this.anim.cancel();
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2.0f;
            float cy = h / 2.0f;
            float r = Math.min(w, h) * 0.27f;

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, r * 2.4f,
                    new int[]{0x335B8CFF, 0x125B8CFF, 0x000B0F14},
                    new float[]{0.0f, 0.55f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 2.4f, paint);
            paint.setShader(null);

            for (int i = 0; i < 3; i++) {
                float p = (t + i / 3.0f) % 1.0f;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.15f * this.density);
                paint.setColor(Color.argb((int) (64.0f * (1.0f - p)), 91, 140, 255));
                canvas.drawCircle(cx, cy, r * (1.08f + p * 0.62f), paint);
            }

            rect.set(cx - r * 1.32f, cy - r * 1.32f, cx + r * 1.32f, cy + r * 1.32f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(2.1f * this.density);
            paint.setShader(new SweepGradient(cx, cy,
                    new int[]{0x005B8CFF, 0x0022D3EE, 0xFF22D3EE, 0x005B8CFF},
                    new float[]{0.0f, 0.55f, 0.85f, 1.0f}));
            canvas.save();
            canvas.rotate(t * 360.0f, cx, cy);
            canvas.drawArc(rect, -34.0f, 190.0f, false, paint);
            canvas.restore();
            paint.setShader(null);
            paint.setStrokeCap(Paint.Cap.BUTT);

            float s = r * 0.92f;
            rect.set(cx - s, cy - s, cx + s, cy + s);
            float rr = s * 0.3f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF121A24);
            canvas.drawRoundRect(rect, rr, rr, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f * this.density);
            paint.setColor(0xFF2A405C);
            canvas.drawRoundRect(rect, rr, rr, paint);

            canvas.save();
            clip.reset();
            clip.addRoundRect(rect, rr, rr, Path.Direction.CW);
            canvas.clipPath(clip);
            float slide = (t * 2.6f - 0.8f) * s * 2.0f;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(cx - s + slide, cy - s,
                    cx - s + slide + s * 0.8f, cy + s,
                    new int[]{0x00FFFFFF, 0x14FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, paint);
            canvas.restore();
            paint.setShader(null);

            String glyph = ">_";
            float tw = textPaint.measureText(glyph);
            float tx = cx - tw / 2.0f;
            float ty = cy - (textPaint.descent() + textPaint.ascent()) / 2.0f;
            canvas.drawText(glyph, cx, ty, textPaint);

            float blink = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 4.0f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int) (60.0f + 195.0f * blink), 34, 211, 238));
            float caretW = 3.2f * this.density;
            float caretH = 22.0f * this.density;
            canvas.drawRoundRect(tx + tw + 7.0f * this.density, ty - caretH * 0.78f,
                    tx + tw + 7.0f * this.density + caretW, ty + caretH * 0.22f,
                    1.6f * this.density, 1.6f * this.density, paint);

            double angle = t * Math.PI * 4.0 - Math.PI / 2.0;
            float ox = cx + (float) Math.cos(angle) * r * 1.32f;
            float oy = cy + (float) Math.sin(angle) * r * 1.32f;
            paint.setColor(0x3322D3EE);
            canvas.drawCircle(ox, oy, 8.0f * this.density, paint);
            paint.setColor(0xFF22D3EE);
            canvas.drawCircle(ox, oy, 3.0f * this.density, paint);

            for (int i = 0; i < 20; i++) {
                double a = i * 2.399963229728653 + t * 0.35;
                float rad = r * (1.45f + (i % 7) * 0.055f);
                paint.setColor(Color.argb(35 + (i % 4) * 14, 139, 160, 184));
                canvas.drawCircle(cx + (float) Math.cos(a) * rad,
                        cy + (float) Math.sin(a) * rad, 1.05f * this.density, paint);
            }
        }
    }
}
