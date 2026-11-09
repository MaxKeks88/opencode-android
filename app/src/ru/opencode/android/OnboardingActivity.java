package ru.opencode.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/* loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public class OnboardingActivity extends Activity {
    private TextView battStatus;
    private TextView filesStatus;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        float f = getResources().getDisplayMetrics().density;
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(-16052460);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(22), dp(30), dp(22), dp(22));
        scrollView.addView(linearLayout, new ViewGroup.LayoutParams(-1, -2));
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        layoutParams.gravity = 1;
        imageView.setLayoutParams(layoutParams);
        linearLayout.addView(imageView);
        TextView textView = new TextView(this);
        textView.setText("OpenCode на телефоне");
        textView.setTextSize(24.0f);
        textView.setTextColor(-1);
        textView.setGravity(17);
        textView.setPadding(0, dp(12), 0, dp(6));
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText("Настоящий OpenCode работает прямо на телефоне: чаты, модели, файлы, git и инструменты. Без ПК.");
        textView2.setTextSize(13.0f);
        textView2.setTextColor(-7626568);
        textView2.setGravity(17);
        textView2.setPadding(0, 0, 0, dp(18));
        linearLayout.addView(textView2);
        linearLayout.addView(head("1. Доступ к файлам"));
        linearLayout.addView(note("Нужен, чтобы читать и изменять файлы на телефоне."));
        this.filesStatus = note("");
        linearLayout.addView(this.filesStatus);
        Button btn = btn("Разрешить доступ ко всем файлам");
        btn.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.OnboardingActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OnboardingActivity.this.m15lambda$onCreate$0$ruopencodeandroidOnboardingActivity(view);
            }
        });
        linearLayout.addView(btn);
        linearLayout.addView(head("2. Батарея"));
        linearLayout.addView(note("Чтобы OpenCode не засыпал в фоне."));
        this.battStatus = note("");
        linearLayout.addView(this.battStatus);
        Button btn2 = btn("Отключить оптимизацию батареи");
        btn2.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.OnboardingActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OnboardingActivity.this.m16lambda$onCreate$1$ruopencodeandroidOnboardingActivity(view);
            }
        });
        linearLayout.addView(btn2);
        linearLayout.addView(head("3. Рабочая папка"));
        linearLayout.addView(note("По умолчанию — " + App.defaultDirectory() + ". Папку можно сменить в настройках."));
        linearLayout.addView(head("4. Аккаунт (необязательно)"));
        linearLayout.addView(note("Войдите в OpenCode, чтобы получить платные модели Go. Это можно сделать позже: шестерёнка → Провайдеры."));
        Button btn3 = btn("Начать работу");
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = dp(22);
        btn3.setLayoutParams(layoutParams2);
        btn3.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.OnboardingActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OnboardingActivity.this.m17lambda$onCreate$2$ruopencodeandroidOnboardingActivity(view);
            }
        });
        linearLayout.addView(btn3);
        TextView textView3 = new TextView(this);
        textView3.setText("Пропустить");
        textView3.setTextColor(-7626568);
        textView3.setGravity(17);
        textView3.setPadding(0, dp(14), 0, 0);
        textView3.setOnClickListener(new View.OnClickListener() { // from class: ru.opencode.android.OnboardingActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OnboardingActivity.this.m18lambda$onCreate$3$ruopencodeandroidOnboardingActivity(view);
            }
        });
        linearLayout.addView(textView3);
        setContentView(scrollView);
        Lang.localizeView(scrollView);
        updateStatus();
    }

    /* renamed from: lambda$onCreate$0$ru-opencode-android-OnboardingActivity, reason: not valid java name */
    /* synthetic */ void m15lambda$onCreate$0$ruopencodeandroidOnboardingActivity(View view) {
        openAllFiles();
    }

    /* renamed from: lambda$onCreate$1$ru-opencode-android-OnboardingActivity, reason: not valid java name */
    /* synthetic */ void m16lambda$onCreate$1$ruopencodeandroidOnboardingActivity(View view) {
        openBattery();
    }

    /* renamed from: lambda$onCreate$2$ru-opencode-android-OnboardingActivity, reason: not valid java name */
    /* synthetic */ void m17lambda$onCreate$2$ruopencodeandroidOnboardingActivity(View view) {
        finishOnboarding();
    }

    /* renamed from: lambda$onCreate$3$ru-opencode-android-OnboardingActivity, reason: not valid java name */
    /* synthetic */ void m18lambda$onCreate$3$ruopencodeandroidOnboardingActivity(View view) {
        finishOnboarding();
    }

    private void finishOnboarding() {
        App.prefs().edit().putBoolean("onboarded", true).apply();
        startActivity(new Intent(this, (Class<?>) MainActivity.class));
        finish();
    }

    private void updateStatus() {
        if (this.filesStatus != null) {
            this.filesStatus.setText(App.allFilesGranted() ? "✓ доступ есть" : "✗ доступа нет");
            this.filesStatus.setTextColor(App.allFilesGranted() ? -13315175 : -495247);
        }
        if (this.battStatus != null) {
            boolean isBatteryOk = isBatteryOk();
            this.battStatus.setText(isBatteryOk ? "✓ оптимизация отключена" : "• оптимизация включена (не критично)");
            this.battStatus.setTextColor(isBatteryOk ? -13315175 : -7626568);
        }
    }

    private boolean isBatteryOk() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService("power");
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(getPackageName());
            }
            return false;
        } catch (Throwable th) {
            return false;
        }
    }

    private void openAllFiles() {
        try {
            startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", Uri.parse("package:" + getPackageName())));
        } catch (Throwable th) {
            try {
                startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"));
            } catch (Throwable th2) {
            }
        }
    }

    private void openBattery() {
        try {
            startActivity(new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", Uri.parse("package:" + getPackageName())));
        } catch (Throwable th) {
            try {
                startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
            } catch (Throwable th2) {
            }
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        updateStatus();
        Lang.localizeView(getWindow().getDecorView());
        try {
            if (App.prefs().getBoolean("keep_screen_on", false)) {
                getWindow().addFlags(128);
            }
        } catch (Throwable th) {
        }
    }

    private TextView head(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(16.0f);
        textView.setTextColor(-1);
        textView.setPadding(0, dp(16), 0, dp(4));
        return textView;
    }

    private TextView note(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(12.5f);
        textView.setTextColor(-7626568);
        textView.setPadding(0, 0, 0, dp(6));
        return textView;
    }

    private Button btn(String str) {
        Button button = new Button(this);
        button.setText(str);
        button.setTextColor(-1);
        button.getBackground().setAlpha(60);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(6);
        button.setLayoutParams(layoutParams);
        return button;
    }

    private int dp(int i) {
        return (int) (i * getResources().getDisplayMetrics().density);
    }
}
