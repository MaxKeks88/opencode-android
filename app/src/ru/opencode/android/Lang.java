package ru.opencode.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Язык интерфейса: en (по умолчанию для новых установок) и ru. */
public final class Lang {
    public static final String PREF = "lang";
    public static final String EN = "en";
    public static final String RU = "ru";
    public static final String ZH = "zh";
    public static final String ES = "es";

    private static final Map<String, String> EN_MAP = new HashMap<String, String>();
    private static final List<Map.Entry<String, String>> LONG =
            new ArrayList<Map.Entry<String, String>>();

    static {
        put("OpenCode на телефоне", "OpenCode on your phone");
        put("Настоящий OpenCode работает прямо на телефоне: чаты, модели, файлы, git и инструменты. Без ПК.",
            "Real OpenCode runs right on your phone: chats, models, files, git and tools. No PC required.");
        put("1. Доступ к файлам", "1. File access");
        put("2. Батарея", "2. Battery");
        put("3. Рабочая папка", "3. Working folder");
        put("4. Аккаунт (необязательно)", "4. Account (optional)");
        put("Нужен, чтобы читать и изменять файлы на телефоне.", "Needed to read and modify files on the phone.");
        put("Чтобы OpenCode не засыпал в фоне.", "Keeps OpenCode from sleeping in the background.");
        put("По умолчанию — ", "Default — ");
        put(". Папку можно сменить в настройках.", ". You can change the folder in settings.");
        put("Войдите в OpenCode, чтобы получить платные модели Go. Это можно сделать позже: шестерёнка → Провайдеры.",
            "Sign in to OpenCode to get paid Go models. You can do this later: gear → Providers.");
        put("Разрешить доступ ко всем файлам", "Allow all files access");
        put("Отключить оптимизацию батареи", "Disable battery optimization");
        put("Начать работу", "Start");
        put("Пропустить", "Skip");
        put("✓ доступ есть", "✓ access granted");
        put("✗ доступа нет", "✗ no access");
        put("✓ оптимизация отключена", "✓ optimization disabled");
        put("• оптимизация включена (не критично)", "• optimization on (not critical)");

        put("Системные настройки OpenCode", "OpenCode system settings");
        put("Рабочая папка (проекты)", "Working folder (projects)");
        put("Сохранить папку", "Save folder");
        put("Лимит времени для shell-команд, сек (0 — без лимита)", "Shell time limit, sec (0 = no limit)");
        put("Сохранить лимит shell", "Save shell limit");
        put("Лимит вывода shell, МБ (0 — без лимита)", "Shell output limit, MB (0 = no limit)");
        put("Сохранить лимит вывода", "Save output limit");
        put("Проверить уведомление", "Test notification");
        put("Перезапустить OpenCode", "Restart OpenCode");
        put("Показать журнал", "Show log");
        put("Закрыть", "Close");
        put("Экспорт логов (ZIP)", "Export logs (ZIP)");
        put("Экспорт данных (сессии, ключи)", "Export data (sessions, keys)");
        put("Импорт данных из ZIP", "Import data from ZIP");
        put("Экспорт сохраняет сессии, настройки, SSH-ключи в /sdcard/Download. Импорт заменяет их данными из ZIP и перезапускает OpenCode.",
            "Export saves sessions, settings and SSH keys to /sdcard/Download. Import replaces them from a ZIP and restarts OpenCode.");
        put("Ключ OpenCode Zen и вход по аккаунту — в разделе «Провайдеры» в приложении (шестерёнка → Провайдеры).",
            "OpenCode Zen key and account login are in Providers in the app (gear → Providers).");
        put("Проверить обновления OpenCode", "Check OpenCode updates");
        put("Проверить обновления приложения", "Check app updates");
        put("Канал: стабильный (нажмите для предрелизов)", "Channel: stable (tap for pre-releases)");
        put("Канал: предрелизы (нажмите для стабильного)", "Channel: pre-releases (tap for stable)");
        put("Канал: стабильный", "Channel: stable");
        put("Канал: предрелизы", "Channel: pre-releases");
        put("Автопроверка обновлений приложения: вкл", "App update auto-check: on");
        put("Автопроверка обновлений приложения: выкл", "App update auto-check: off");
        put("Проверяю обновления…", "Checking for updates…");
        put("Проверяю обновления приложения…", "Checking app updates…");
        put("Скачивание обновления…", "Downloading update…");
        put("Обновление OpenCode", "OpenCode update");
        put("Обновление приложения", "App update");
        put("Скачать", "Download");
        put("Позже", "Later");
        put("Отмена", "Cancel");
        put("Ошибка", "Error");
        put("Журнал", "Log");
        put("Перезапуск…", "Restarting…");
        put("Уведомления работают", "Notifications work");
        put("Лимит shell выключен", "Shell limit disabled");
        put("Лимит вывода shell выключен", "Shell output limit disabled");
        put("работает", "running");
        put("не запущен", "not running");
        put("вкл", "on");
        put("выкл", "off");
        put("есть", "available");
        put("нет", "no");
        put("файлов", "files");

        put("Запуск…", "Starting…");
        put("Подготовка…", "Preparing…");
        put("Запуск OpenCode…", "Starting OpenCode…");
        put("OpenCode работает", "OpenCode is running");
        put("Ответ готов", "Answer ready");
        put("Ждёт ответа", "Waiting for your answer");
        put("Ошибка выполнения", "Execution error");
        put("Остановлено", "Stopped");
        put("Стоп", "Stop");
        put("Сообщения OpenCode", "OpenCode messages");
        put("Запуск OpenCode… первый раз может занять до минуты", "Starting OpenCode… first launch may take a minute");
        put("OpenCode не запустился", "OpenCode did not start");
        put("Не удалось запустить", "Failed to start");
        put("Откройте настройки и посмотрите журнал", "Open settings and check the log");
        put("Открыть настройки", "Open settings");
        put("Настройки", "Settings");
        put("Выбрать файл", "Choose file");
        put("Доступ к файлам", "File access");
        put("OpenCode нужно читать и изменять файлы на телефоне. Разрешите «Доступ ко всем файлам» для этого приложения.",
            "OpenCode needs to read and modify files on your phone. Allow “All files access” for this app.");
        put("Открываю чат…", "Opening chat…");

        put("Проверка уже идёт", "Check already in progress");
        put("Доступно обновление ", "Update available ");
        put("Обновлений нет", "No updates available");
        put("Установлена последняя версия ", "You have the latest version ");
        put("Не удалось проверить обновления: ", "Failed to check for updates: ");
        put("Уже идёт проверка или загрузка", "A check or download is already in progress");
        put("Разрешите установку из этого источника и нажмите «Скачать» ещё раз",
                "Allow installing from this source and tap Download again");
        put("Ошибка обновления: ", "Update error: ");
        put("Скачано. Подтвердите установку в системном окне.",
                "Downloaded. Confirm installation in the system window.");
        put("Обновления приложения", "App updates");
        put("Скачивание: ", "Downloading: ");
        put("Обновить", "Update");
        put("Скачать и установить?", "Download and install?");
        put(").\n\nСкачать и установить?", ").\n\nDownload and install?");
        put(". Скачать и установить?", ". Download and install?");
        put("OpenCode ждёт вашего ответа", "OpenCode is waiting for your answer");
        put("OpenCode перезапускается…", "OpenCode is restarting…");
        put("OpenCode не ответил за 90 секунд", "OpenCode did not respond within 90 seconds");
        put("Ошибка: ", "Error: ");
        put("Сохранено: ", "Saved: ");
        put("Логи: ", "Logs: ");
        put("Доступно ", "Available ");
        put(" (установлено ", " (installed ");
        put("Обновлено. OpenCode перезапускается.", "Updated. OpenCode is restarting.");
        put("Обновление прервано", "Update interrupted");
        put("Ошибка экспорта логов: ", "Log export error: ");
        put("нет релизов v2.x", "no v2.x releases");
        put("нет ассета opencode-linux-arm64-musl.tar.gz", "no opencode-linux-arm64-musl.tar.gz asset");
        put("нет ссылки на ассет", "no asset link");
        put("контрольная сумма не совпала", "checksum mismatch");
        put("скачанный файл не ELF", "downloaded file is not ELF");
        put("бинарник слишком мал", "binary is too small");
        put("не удалось сделать бэкап", "could not create a backup");
        put("не удалось заменить бинарник", "could not replace the binary");
        put("opencode не найден в архиве", "opencode not found in the archive");
        put("последний релиз помечен как черновик/предрелиз", "latest release is a draft/prerelease");
        put("у релиза нет тега", "release has no tag");
        put("в релизе нет APK-ассета", "release has no APK asset");
        put("у ассета нет ссылки", "asset has no link");
        put("нет ссылки на APK", "no APK link");
        put("не удалось создать ", "could not create ");
        put("не удалось заменить старый APK", "could not replace the old APK");
        put("не удалось переименовать APK", "could not rename the APK");
        put("скачанный файл не APK", "downloaded file is not an APK");
        put("чужой пакет: ", "different package: ");
        put("версия в APK не новее установленной", "APK version is not newer than installed");
        put("APK подписан другим ключом — установка поверх невозможна",
                "APK is signed with a different key — in-place update is impossible");
        put("файл слишком мал: ", "file is too small: ");
        put("Shizuku не запущен", "Shizuku is not running");
        put("[команда остановлена: лимит ", "[command stopped: limit ");
        put(" с. Изменить: ⚙ Настройки → «Лимит shell»]", " s. Change it: ⚙ Settings → “Shell limit”]");
        put("… (вывод обрезан, лимит ", "… (output truncated, limit ");
        put(" МБ: ⚙ Настройки → «Лимит вывода shell»)", " MB: ⚙ Settings → “Shell output limit”)");

        /* Длинные фразы можно заменять внутри склеенных строк. */
        for (Map.Entry<String, String> e : EN_MAP.entrySet()) {
            String key = e.getKey();
            if (key.length() >= 14 && key.indexOf(' ') >= 0) {
                LONG.add(e);
            }
        }
        LONG.sort(new Comparator<Map.Entry<String, String>>() {
            @Override
            public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                return b.getKey().length() - a.getKey().length();
            }
        });
    }

    private Lang() {
    }

    private static void put(String ru, String en) {
        EN_MAP.put(ru, en);
    }

    public static String get() {
        try {
            String value = App.prefs().getString(PREF, "");
            if (value != null && !value.isEmpty()) {
                return value;
            }
            return App.prefs().getBoolean("onboarded", false) ? RU : EN;
        } catch (Throwable th) {
            return RU;
        }
    }

    public static boolean isEn() {
        return EN.equals(get());
    }

    public static boolean isZh() {
        return ZH.equals(get());
    }

    public static boolean isEs() {
        return ES.equals(get());
    }

    public static void set(String code) {
        try {
            App.prefs().edit().putString(PREF, code).apply();
        } catch (Throwable th) {
        }
    }

    public static String t(String ru, String en) {
        if (isZh()) {
            String zh = LangZh.MAP.get(ru);
            return zh != null ? zh : ru;
        }
        if (isEs()) {
            String es = LangEs.MAP.get(ru);
            return es != null ? es : ru;
        }
        return isEn() ? en : ru;
    }

    /** Перевод готовой строки: точное совпадение или частые шаблоны. */
    public static String tr(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        if (isZh()) {
            String zh = LangZh.MAP.get(source);
            return zh != null ? zh : LangZh.apply(source);
        }
        if (isEs()) {
            String es = LangEs.MAP.get(source);
            return es != null ? es : LangEs.apply(source);
        }
        if (!isEn()) {
            return source;
        }
        String exact = EN_MAP.get(source);
        if (exact != null) {
            return exact;
        }
        String text = source
            .replace(" (установлено ", " (installed ")
            .replace(" (ошибка чтения: ", " (read error: ")
            .replace(" (нет файла ", " (missing file ")
            .replace("Доступно ", "Available ")
            .replace("Установлено ", "Installed ")
            .replace("Установлена последняя версия ", "You have the latest version ")
            .replace(", доступно ", ", available ")
            .replace("Не удалось проверить обновления: ", "Failed to check for updates: ")
            .replace("Не удалось открыть выбор файла: ", "Failed to open file picker: ")
            .replace("Импортировано файлов: ", "Files imported: ")
            .replace("Ошибка импорта: ", "Import error: ")
            .replace("Ошибка экспорта: ", "Export error: ")
            .replace("Сохранено: ", "Saved: ")
            .replace("Логи: ", "Logs: ")
            .replace("Экспорт: ", "Export: ")
            .replace("Лимит shell: ", "Shell limit: ")
            .replace("Лимит вывода shell: ", "Shell output limit: ")
            .replace("архив больше ", "archive is larger than ")
            .replace("в архиве больше ", "archive contains more than ")
            .replace("По умолчанию — ", "Default — ")
            .replace(". Папку можно сменить в настройках.", ". You can change the folder in settings.")
            .replace("OpenCode: работает", "OpenCode: running")
            .replace("OpenCode: не запущен", "OpenCode: not running")
            .replace("Сервер: работает", "Server: running")
            .replace("Сервер: не запущен", "Server: not running")
            .replace("Доступ к файлам: есть", "File access: granted")
            .replace("Доступ к файлам: нет", "File access: no")
            .replace("Порт: ", "Port: ")
            .replace("Ошибка: ", "Error: ")
            .replace("Ошибка обновления: ", "Update error: ")
            .replace("Скачивание: ", "Downloading: ");
        for (Map.Entry<String, String> e : LONG) {
            if (text.contains(e.getKey())) {
                text = text.replace(e.getKey(), e.getValue());
            }
        }
        text = text.replaceAll("(\\d+)\\s+файлов", "$1 files");
        text = text.replaceAll("(\\d+)\\s+МБ", "$1 MB");
        text = text.replaceAll("(\\d+)\\s+с\\b", "$1 s");
        return text;
    }

    /** Переводит подписи всех TextView/Button и подсказки EditText. */
    public static void localizeView(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof TextView && !(view instanceof EditText)) {
            TextView text = (TextView) view;
            CharSequence current = text.getText();
            if (current != null) {
                String translated = tr(current.toString());
                if (!translated.equals(current.toString())) {
                    text.setText(translated);
                }
            }
        }
        if (view instanceof EditText) {
            CharSequence hint = ((EditText) view).getHint();
            if (hint != null) {
                String translated = tr(hint.toString());
                if (!translated.equals(hint.toString())) {
                    ((EditText) view).setHint(translated);
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                localizeView(group.getChildAt(i));
            }
        }
    }

    public static String languageName(Context context, String code) {
        if (EN.equals(code)) {
            return "English";
        }
        if (RU.equals(code)) {
            return "Русский";
        }
        if (ZH.equals(code)) {
            return "中文（简体）";
        }
        if (ES.equals(code)) {
            return "Español";
        }
        return code;
    }
}
