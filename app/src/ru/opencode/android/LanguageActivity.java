package ru.opencode.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** Первый экран новой установки: выбор языка (English по умолчанию). */
public class LanguageActivity extends Activity {
    private RadioGroup group;
    private RadioButton en;
    private RadioButton ru;
    private RadioButton zh;
    private RadioButton zhHant;
    private RadioButton es;
    private RadioButton pt;
    private RadioButton de;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(-16052460);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(-1, -2));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(88), dp(88));
        iconParams.gravity = 1;
        icon.setLayoutParams(iconParams);
        root.addView(icon);

        TextView title = new TextView(this);
        title.setText("Choose language / Выберите язык");
        title.setTextSize(22.0f);
        title.setTextColor(-1);
        title.setGravity(17);
        title.setPadding(0, dp(16), 0, dp(4));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("OpenCode will use this language. You can change it later in Settings.");
        sub.setTextSize(13.0f);
        sub.setTextColor(-7626568);
        sub.setGravity(17);
        sub.setPadding(0, 0, 0, dp(18));
        root.addView(sub);

        this.group = new RadioGroup(this);
        this.group.setOrientation(1);
        this.en = option("English 🇬🇧", "English interface");
        this.ru = option("Русский 🇷🇺", "Русский интерфейс");
        this.zh = option("中文（简体） 🇨🇳", "简体中文界面");
        this.zhHant = option("中文（繁體） 🇹🇼", "繁體中文介面");
        this.es = option("Español 🇪🇸", "Interfaz en español");
        this.pt = option("Português (Brasil) 🇧🇷", "Interface em português");
        this.de = option("Deutsch 🇩🇪", "Deutsche Oberfläche");
        this.en.setId(1001);
        this.ru.setId(1002);
        this.zh.setId(1003);
        this.es.setId(1004);
        this.zhHant.setId(1005);
        this.pt.setId(1006);
        this.de.setId(1007);
        this.group.addView(this.en);
        this.group.addView(this.ru);
        this.group.addView(this.zh);
        this.group.addView(this.zhHant);
        this.group.addView(this.es);
        this.group.addView(this.pt);
        this.group.addView(this.de);
        String current = Lang.get();
        this.group.check(Lang.RU.equals(current) ? this.ru.getId()
                : (Lang.ZH.equals(current) ? this.zh.getId()
                : (Lang.ZH_HANT.equals(current) ? this.zhHant.getId()
                : (Lang.ES.equals(current) ? this.es.getId()
                : (Lang.PT.equals(current) ? this.pt.getId()
                : (Lang.DE.equals(current) ? this.de.getId() : this.en.getId()))))));
        root.addView(this.group);

        TextView more = new TextView(this);
        more.setText("More languages are coming soon. / Другие языки скоро.");
        more.setTextSize(12.0f);
        more.setTextColor(-7626568);
        more.setGravity(17);
        more.setPadding(0, dp(16), 0, 0);
        root.addView(more);

        Button next = new Button(this);
        next.setText("Continue / Продолжить");
        next.setTextColor(-1);
        next.getBackground().setAlpha(60);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-1, -2);
        nextParams.topMargin = dp(22);
        next.setLayoutParams(nextParams);
        next.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                int checked = group.getCheckedRadioButtonId();
                Lang.set(checked == ru.getId() ? Lang.RU
                        : (checked == zh.getId() ? Lang.ZH
                        : (checked == zhHant.getId() ? Lang.ZH_HANT
                        : (checked == es.getId() ? Lang.ES
                        : (checked == pt.getId() ? Lang.PT
                        : (checked == de.getId() ? Lang.DE : Lang.EN))))));
                Class<?> nextClass = App.prefs().getBoolean("onboarded", false)
                        ? MainActivity.class : OnboardingActivity.class;
                startActivity(new Intent(LanguageActivity.this, nextClass));
                finish();
            }
        });
        root.addView(next);

        setContentView(scroll);
    }

    private RadioButton option(String label, String note) {
        RadioButton button = new RadioButton(this);
        button.setText(label + " — " + note);
        button.setTextColor(-1);
        button.setTextSize(16.0f);
        button.setPadding(0, dp(10), 0, dp(10));
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
