package ru.opencode.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;
import ru.opencode.android.Updater;

/* loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public class SettingsActivity extends Activity {
    private static final int REQ_IMPORT = 201;
    /* Пределы импорта: бэкап проходит, ZIP-бомба — нет. */
    private static final int MAX_BACKUP_FILES = 20000;
    private static final long MAX_BACKUP_BYTES = 536870912L;
    private EditText dirEdit;
    private EditText shEdit;
    private EditText outEdit;
    private TextView info;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        float f = getResources().getDisplayMetrics().density;
        int i = (int) (18.0f * f);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(-16052460);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(i, i, i, i);
        scrollView.addView(linearLayout);
        TextView textView = new TextView(this);
        textView.setText("Системные настройки OpenCode");
        textView.setTextSize(22.0f);
        textView.setTextColor(-1);
        textView.setPadding(0, 0, 0, (int) (f * 14.0f));
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText("Ключ OpenCode Zen и вход по аккаунту — в разделе «Провайдеры» в приложении (шестерёнка → Провайдеры).");
        textView2.setTextColor(-7626568);
        textView2.setTextSize(12.0f);
        int i2 = (int) (16.0f * f);
        textView2.setPadding(0, 0, 0, i2);
        linearLayout.addView(textView2);
        TextView textView4 = new TextView(this);
        textView4.setText("Рабочая папка (проекты)");
        textView4.setTextColor(-1);
        textView4.setTextSize(15.0f);
        int i4 = (int) (8.0f * f);
        int i5 = (int) (f * 6.0f);
        textView4.setPadding(0, i4, 0, i5);
        linearLayout.addView(textView4);
        this.dirEdit = new EditText(this);
        this.dirEdit.setSingleLine(true);
        this.dirEdit.setText(App.defaultDirectory());
        this.dirEdit.setTextColor(-1);
        linearLayout.addView(this.dirEdit);
        Button btn = btn("Сохранить папку");
        btn.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda19
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m40lambda$onCreate$5$ruopencodeandroidSettingsActivity(view);
            }
        });
        linearLayout.addView(btn);
        TextView textViewSh = new TextView(this);
        textViewSh.setText("Лимит времени для shell-команд, сек (0 — без лимита)");
        textViewSh.setTextColor(-1);
        textViewSh.setTextSize(15.0f);
        textViewSh.setPadding(0, i4, 0, i5);
        linearLayout.addView(textViewSh);
        this.shEdit = new EditText(this);
        this.shEdit.setSingleLine(true);
        this.shEdit.setInputType(2);
        this.shEdit.setText(String.valueOf(App.prefs().getInt("ocsh_timeout", 0)));
        this.shEdit.setTextColor(-1);
        linearLayout.addView(this.shEdit);
        Button btnSh = btn("Сохранить лимит shell");
        btnSh.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                int sec;
                try {
                    sec = Integer.parseInt(SettingsActivity.this.shEdit.getText().toString().trim());
                } catch (Throwable th) {
                    sec = 0;
                }
                if (sec < 0) {
                    sec = 0;
                }
                if (sec > 86400) {
                    sec = 86400;
                }
                App.prefs().edit().putInt("ocsh_timeout", sec).apply();
                Toast.makeText(SettingsActivity.this,
                        sec == 0 ? "Лимит shell выключен" : ("Лимит shell: " + sec + " с"), 0).show();
            }
        });
        linearLayout.addView(btnSh);
        TextView textViewOut = new TextView(this);
        textViewOut.setText("Лимит вывода shell, МБ (0 — без лимита)");
        textViewOut.setTextColor(-1);
        textViewOut.setTextSize(15.0f);
        textViewOut.setPadding(0, i4, 0, i5);
        linearLayout.addView(textViewOut);
        this.outEdit = new EditText(this);
        this.outEdit.setSingleLine(true);
        this.outEdit.setInputType(2);
        this.outEdit.setText(String.valueOf(App.prefs().getInt("ocsh_max_out", 4)));
        this.outEdit.setTextColor(-1);
        linearLayout.addView(this.outEdit);
        Button btnOut = btn("Сохранить лимит вывода");
        btnOut.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                int mb;
                try {
                    mb = Integer.parseInt(SettingsActivity.this.outEdit.getText().toString().trim());
                } catch (Throwable th) {
                    mb = 4;
                }
                if (mb < 0) {
                    mb = 0;
                }
                if (mb > 1024) {
                    mb = 1024;
                }
                App.prefs().edit().putInt("ocsh_max_out", mb).apply();
                Toast.makeText(SettingsActivity.this,
                        mb == 0 ? "Лимит вывода shell выключен" : ("Лимит вывода shell: " + mb + " МБ"), 0).show();
            }
        });
        linearLayout.addView(btnOut);
        Button btn4 = btn("Проверить уведомление");
        btn4.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda22
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m43lambda$onCreate$8$ruopencodeandroidSettingsActivity(view);
            }
        });
        linearLayout.addView(btn4);
        Button btn5 = btn("Перезапустить OpenCode");
        btn5.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda23
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m33lambda$onCreate$10$ruopencodeandroidSettingsActivity(view);
            }
        });
        linearLayout.addView(btn5);
        Button btn6 = btn("Показать журнал");
        btn6.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m34lambda$onCreate$11$ruopencodeandroidSettingsActivity(view);
            }
        });
        linearLayout.addView(btn6);
        Button btnLogs = btn("Экспорт логов (ZIP)");
        btnLogs.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                exportLogs();
            }
        });
        linearLayout.addView(btnLogs);
        Button btn7 = btn("Экспорт данных (сессии, ключи)");
        btn7.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda12
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m35lambda$onCreate$12$ruopencodeandroidSettingsActivity(view);
            }
        });
        linearLayout.addView(btn7);
        Button btn8 = btn("Импорт данных из ZIP");
        btn8.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda13
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m36lambda$onCreate$13$ruopencodeandroidSettingsActivity(view);
            }
        });
        linearLayout.addView(btn8);
        TextView textView5 = new TextView(this);
        textView5.setText("Экспорт сохраняет сессии, настройки, SSH-ключи в /sdcard/Download. Импорт заменяет их данными из ZIP и перезапускает OpenCode.");
        textView5.setTextColor(-7626568);
        textView5.setTextSize(12.0f);
        textView5.setPadding(0, i5, 0, 0);
        linearLayout.addView(textView5);
        final Button btnChannel = btn("");
        btnChannel.setText(Updater.previewChannel() ? "Канал: предрелизы (нажмите для стабильного)"
                                                     : "Канал: стабильный (нажмите для предрелизов)");
        btnChannel.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                boolean preview = !Updater.previewChannel();
                Updater.setPreviewChannel(preview);
                btnChannel.setText(preview ? "Канал: предрелизы (нажмите для стабильного)"
                                           : "Канал: стабильный (нажмите для предрелизов)");
                Toast.makeText(SettingsActivity.this,
                        preview ? "Канал: предрелизы" : "Канал: стабильный", 0).show();
            }
        });
        linearLayout.addView(btnChannel);
        Button btn9 = btn("Проверить обновления OpenCode");
        btn9.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda14
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m37lambda$onCreate$14$ruopencodeandroidSettingsActivity(view);
            }
        });
        linearLayout.addView(btn9);
        this.info = new TextView(this);
        this.info.setTextColor(-7626568);
        this.info.setTextSize(12.0f);
        this.info.setPadding(0, i2, 0, 0);
        linearLayout.addView(this.info);
        setContentView(scrollView);
        checkStatus();
    }

    /* renamed from: lambda$onCreate$5$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m40lambda$onCreate$5$ruopencodeandroidSettingsActivity(View view) {
        String trim = this.dirEdit.getText().toString().trim();
        if (trim.isEmpty()) {
            return;
        }
        File file = new File(trim);
        if (!file.exists()) {
            file.mkdirs();
        }
        App.setDefaultDirectory(trim);
        Toast.makeText(this, "Сохранено: " + trim, 0).show();
    }

    /* renamed from: lambda$onCreate$8$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m43lambda$onCreate$8$ruopencodeandroidSettingsActivity(View view) {
        Notifications.show(this, App.TAG, "Уведомления работают");
    }

    /* renamed from: lambda$onCreate$10$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m33lambda$onCreate$10$ruopencodeandroidSettingsActivity(View view) {
        RuntimeService.stop(this);
        new Handler().postDelayed(new Runnable() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.m44lambda$onCreate$9$ruopencodeandroidSettingsActivity();
            }
        }, 900L);
        Toast.makeText(this, "Перезапуск…", 0).show();
    }

    /* renamed from: lambda$onCreate$9$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m44lambda$onCreate$9$ruopencodeandroidSettingsActivity() {
        RuntimeService.start(this);
    }

    /* renamed from: lambda$onCreate$11$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m34lambda$onCreate$11$ruopencodeandroidSettingsActivity(View view) {
        showLogs();
    }

    /* renamed from: lambda$onCreate$12$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m35lambda$onCreate$12$ruopencodeandroidSettingsActivity(View view) {
        exportBackup();
    }

    /* renamed from: lambda$onCreate$13$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m36lambda$onCreate$13$ruopencodeandroidSettingsActivity(View view) {
        importBackup();
    }

    /* renamed from: lambda$onCreate$14$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m37lambda$onCreate$14$ruopencodeandroidSettingsActivity(View view) {
        checkUpdates();
    }

    private Button btn(String str) {
        Button button = new Button(this);
        button.setText(str);
        button.setTextColor(-1);
        button.getBackground().setAlpha(60);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = (int) (getResources().getDisplayMetrics().density * 8.0f);
        button.setLayoutParams(layoutParams);
        return button;
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        checkStatus();
    }

    private void checkStatus() {
        this.info.setText("OpenCode: " + (App.ocRunning ? "работает" : "не запущен") + "\nСервер: " + (App.serverRunning ? "работает" : "не запущен") + "\nДоступ к файлам: " + (App.allFilesGranted() ? "есть" : "нет") + "\nПорт: " + App.ocPort);
    }

    private JSONObject req(String str, String str2, JSONObject jSONObject, int i) throws Exception {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL("http://127.0.0.1:" + App.ocPort + str2).openConnection();
            httpURLConnection.setRequestMethod(str);
            httpURLConnection.setConnectTimeout(5000);
            httpURLConnection.setReadTimeout(i);
            httpURLConnection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(("opencode:" + App.password).getBytes(StandardCharsets.UTF_8), 2));
            if (jSONObject != null) {
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = jSONObject.toString().getBytes(StandardCharsets.UTF_8);
                httpURLConnection.setFixedLengthStreamingMode(bytes.length);
                OutputStream outputStream = httpURLConnection.getOutputStream();
                outputStream.write(bytes);
                outputStream.close();
            }
            int responseCode = httpURLConnection.getResponseCode();
            InputStream errorStream = responseCode >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (errorStream != null) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                while (true) {
                    String readLine = bufferedReader.readLine();
                    if (readLine == null) {
                        break;
                    }
                    sb.append(readLine);
                }
                bufferedReader.close();
            }
            if (responseCode >= 400) {
                throw new Exception("HTTP " + responseCode + ": " + String.valueOf(sb));
            }
            if (sb.length() != 0) {
                return new JSONObject(sb.toString());
            }
            return new JSONObject();
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }

    private void exportLogs() {
        new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                doExportLogs();
            }
        }, "oc-logs").start();
    }

    private void doExportLogs() {
        final String result = App.exportLogsZip();
        runOnUiThread(new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                Toast.makeText(SettingsActivity.this, result.startsWith("Ошибка") ? result : "Логи: " + result, 1).show();
            }
        });
    }

    private void exportBackup() {
        new Thread(new Runnable() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.m28lambda$exportBackup$16$ruopencodeandroidSettingsActivity();
            }
        }).start();
    }

    /* renamed from: lambda$exportBackup$16$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m28lambda$exportBackup$16$ruopencodeandroidSettingsActivity() {
        String sb;
        try {
            RuntimeService.stop(this);
            Thread.sleep(2500L);
            File file = new File("/sdcard/Download");
            if (!file.exists()) {
                file.mkdirs();
            }
            File file2 = new File(file, "opencode-backup-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".zip");
            ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file2)));
            byte[] bArr = new byte[65536];
            String[] strArr = {".local/share/opencode", ".config/opencode", ".gitconfig", ".ssh"};
            for (int i = 0; i < 4; i++) {
                File file3 = new File(App.home, strArr[i]);
                if (file3.exists()) {
                    addToZip(zipOutputStream, file3, App.home, bArr);
                }
            }
            zipOutputStream.close();
            sb = "Экспорт: " + file2.getAbsolutePath();
        } catch (Throwable th) {
            sb = "Ошибка экспорта: " + String.valueOf(th);
        } finally {
            try {
                RuntimeService.start(this);
            } catch (Throwable th2) {
            }
        }
        final String str = sb;
        runOnUiThread(new Runnable() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.m27lambda$exportBackup$15$ruopencodeandroidSettingsActivity(str);
            }
        });
    }

    /* renamed from: lambda$exportBackup$15$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m27lambda$exportBackup$15$ruopencodeandroidSettingsActivity(String str) {
        Toast.makeText(this, str, 1).show();
    }

    private static void addToZip(ZipOutputStream zipOutputStream, File file, File file2, byte[] bArr) throws Exception {
        if (file.isDirectory()) {
            File[] listFiles = file.listFiles();
            if (listFiles != null) {
                for (File file3 : listFiles) {
                    addToZip(zipOutputStream, file3, file2, bArr);
                }
                return;
            }
            return;
        }
        zipOutputStream.putNextEntry(new ZipEntry(file.getAbsolutePath().substring(file2.getAbsolutePath().length() + 1).replace('\\', '/')));
        FileInputStream fileInputStream = new FileInputStream(file);
        while (true) {
            int read = fileInputStream.read(bArr);
            if (read <= 0) {
                fileInputStream.close();
                zipOutputStream.closeEntry();
                return;
            }
            zipOutputStream.write(bArr, 0, read);
        }
    }

    private void importBackup() {
        try {
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("*/*");
            startActivityForResult(intent, REQ_IMPORT);
        } catch (Throwable th) {
            Toast.makeText(this, "Не удалось открыть выбор файла: " + String.valueOf(th), 1).show();
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i != REQ_IMPORT || i2 != -1 || intent == null || intent.getData() == null) {
            return;
        }
        final Uri data = intent.getData();
        new Thread(new Runnable() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.m30lambda$onActivityResult$18$ruopencodeandroidSettingsActivity(data);
            }
        }).start();
    }

    /* renamed from: lambda$onActivityResult$18$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m30lambda$onActivityResult$18$ruopencodeandroidSettingsActivity(Uri uri) {
        String sb;
        try {
            RuntimeService.stop(this);
            Thread.sleep(2500L);
            String canonicalPath = App.home.getCanonicalPath();
            ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(getContentResolver().openInputStream(uri)));
            byte[] bArr = new byte[65536];
            int i = 0;
            int entries = 0;
            long total = 0L;
            while (true) {
                ZipEntry nextEntry = zipInputStream.getNextEntry();
                if (nextEntry == null) {
                    break;
                }
                entries++;
                if (entries > MAX_BACKUP_FILES) {
                    throw new Exception("в архиве больше " + MAX_BACKUP_FILES + " файлов");
                }
                String name = nextEntry.getName();
                if (name != null && !name.isEmpty() && !name.contains("..")) {
                    File file = new File(App.home, name);
                    String canonicalPath2 = file.getCanonicalPath();
                    if (!canonicalPath2.equals(canonicalPath) && !canonicalPath2.startsWith(canonicalPath + File.separator)) {
                        zipInputStream.closeEntry();
                        continue;
                    }
                    if (nextEntry.isDirectory()) {
                        file.mkdirs();
                    } else {
                        File parentFile = file.getParentFile();
                        if (parentFile != null) {
                            parentFile.mkdirs();
                        }
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        try {
                            while (true) {
                                int read = zipInputStream.read(bArr);
                                if (read <= 0) {
                                    break;
                                }
                                total += read;
                                if (total > MAX_BACKUP_BYTES) {
                                    throw new Exception("архив больше " + (MAX_BACKUP_BYTES / 1048576L) + " МБ");
                                }
                                fileOutputStream.write(bArr, 0, read);
                            }
                        } finally {
                            fileOutputStream.close();
                        }
                        i++;
                    }
                    zipInputStream.closeEntry();
                }
            }
            zipInputStream.close();
            sb = "Импортировано файлов: " + i;
        } catch (Throwable th) {
            sb = "Ошибка импорта: " + String.valueOf(th);
        } finally {
            try {
                RuntimeService.start(this);
            } catch (Throwable th2) {
            }
        }
        final String str = sb;
        runOnUiThread(new Runnable() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda8
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.m29lambda$onActivityResult$17$ruopencodeandroidSettingsActivity(str);
            }
        });
    }

    /* renamed from: lambda$onActivityResult$17$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m29lambda$onActivityResult$17$ruopencodeandroidSettingsActivity(String str) {
        Toast.makeText(this, str, 1).show();
    }

    private void checkUpdates() {
        Toast.makeText(this, "Проверяю обновления…", 0).show();
        new Thread(new Runnable() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.m26lambda$checkUpdates$21$ruopencodeandroidSettingsActivity();
            }
        }).start();
    }

    /* renamed from: lambda$checkUpdates$21$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m26lambda$checkUpdates$21$ruopencodeandroidSettingsActivity() {
        try {
            final JSONObject latestV2 = Updater.latestV2();
            final String optString = latestV2.optString("tag_name");
            final String optString2 = req("GET", "/api/info", null, 10000).optString("version");
            App.log("обновление: установлено=" + optString2 + ", доступно=" + optString);
            if (!Updater.isNewer(optString, optString2)) {
                m45lambda$startUpdate$22$ruopencodeandroidSettingsActivity("Установлена последняя версия " + optString2);
            } else {
                runOnUiThread(new Runnable() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda9
                    @Override // java.lang.Runnable
                    public final void run() {
                        SettingsActivity.this.m25lambda$checkUpdates$20$ruopencodeandroidSettingsActivity(optString2, optString, latestV2);
                    }
                });
            }
        } catch (Throwable th) {
            m45lambda$startUpdate$22$ruopencodeandroidSettingsActivity("Не удалось проверить обновления: " + String.valueOf(th));
        }
    }

    /* renamed from: lambda$checkUpdates$20$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m25lambda$checkUpdates$20$ruopencodeandroidSettingsActivity(String str, String str2, final JSONObject jSONObject) {
        new AlertDialog.Builder(this).setTitle("Обновление OpenCode").setMessage("Установлено " + str + ", доступно " + str2 + ". Скачать и установить?").setPositiveButton("Обновить", new DialogInterface.OnClickListener() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda7
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                SettingsActivity.this.m24lambda$checkUpdates$19$ruopencodeandroidSettingsActivity(jSONObject, dialogInterface, i);
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* renamed from: lambda$checkUpdates$19$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m24lambda$checkUpdates$19$ruopencodeandroidSettingsActivity(JSONObject jSONObject, DialogInterface dialogInterface, int i) {
        startUpdate(jSONObject);
    }

    private void startUpdate(JSONObject jSONObject) {
        Toast.makeText(this, "Скачивание обновления…", 1).show();
        Updater.downloadAndInstall(this, jSONObject, new Updater.Callback() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda6
            @Override // ru.opencode.android.Updater.Callback
            public final void done(String str) {
                SettingsActivity.this.m45lambda$startUpdate$22$ruopencodeandroidSettingsActivity(str);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: ui, reason: merged with bridge method [inline-methods] */
    public void m45lambda$startUpdate$22$ruopencodeandroidSettingsActivity(final String str) {
        App.log("ui: " + str);
        runOnUiThread(new Runnable() { // from class: ru.opencode.android.SettingsActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.m46lambda$ui$23$ruopencodeandroidSettingsActivity(str);
            }
        });
    }

    /* renamed from: lambda$ui$23$ru-opencode-android-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m46lambda$ui$23$ruopencodeandroidSettingsActivity(String str) {
        Toast.makeText(this, str, 1).show();
    }

    private void showLogs() {
        StringBuilder sb = new StringBuilder();
        append(sb, new File(App.logs, "app.log"), 200);
        sb.append("\n--- opencode ---\n");
        append(sb, new File(App.logs, "opencode.log"), 200);
        TextView textView = new TextView(this);
        textView.setText(sb.toString());
        textView.setTextSize(10.0f);
        textView.setTextColor(-3090208);
        textView.setTextIsSelectable(true);
        textView.setPadding(16, 16, 16, 16);
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(textView);
        new AlertDialog.Builder(this).setTitle("Журнал").setView(scrollView).setPositiveButton("Закрыть", (DialogInterface.OnClickListener) null).show();
    }

    private void append(StringBuilder sb, File file, int i) {
        if (!file.isFile()) {
            sb.append("(нет файла ").append(file.getName()).append(")\n");
            return;
        }
        try {
            ArrayList arrayList = new ArrayList();
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            while (true) {
                String readLine = bufferedReader.readLine();
                if (readLine == null) {
                    break;
                }
                arrayList.add(readLine);
                if (arrayList.size() > i) {
                    arrayList.remove(0);
                }
            }
            bufferedReader.close();
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                sb.append((String) it.next()).append('\n');
            }
        } catch (Throwable th) {
            sb.append("(ошибка чтения: ").append(th).append(")\n");
        }
    }
}
