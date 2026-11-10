package ru.opencode.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deutsch (de). Aus den russischen Quellstrings übersetzt. */
public final class LangDe {
    public static final Map<String, String> MAP = new HashMap<String, String>();
    static final List<String[]> SUBS = new ArrayList<String[]>();
    static final List<Map.Entry<String, String>> LONG = new ArrayList<Map.Entry<String, String>>();

    static {
        put("OpenCode на телефоне", "OpenCode auf deinem Telefon");
        put("Настоящий OpenCode работает прямо на телефоне: чаты, модели, файлы, git и инструменты. Без ПК.", "Echtes OpenCode läuft direkt auf dem Telefon: Chats, Modelle, Dateien, git und Tools. Ohne PC.");
        put("1. Доступ к файлам", "1. Dateizugriff");
        put("2. Батарея", "2. Akku");
        put("3. Рабочая папка", "3. Arbeitsordner");
        put("4. Аккаунт (необязательно)", "4. Konto (optional)");
        put("Нужен, чтобы читать и изменять файлы на телефоне.", "Nötig, um Dateien auf dem Telefon zu lesen und zu ändern.");
        put("Чтобы OpenCode не засыпал в фоне.", "Damit OpenCode im Hintergrund nicht einschläft.");
        put("По умолчанию — ", "Standard — ");
        put(". Папку можно сменить в настройках.", ". Der Ordner lässt sich in den Einstellungen ändern.");
        put("Войдите в OpenCode, чтобы получить платные модели Go. Это можно сделать позже: шестерёнка → Провайдеры.", "Melde dich bei OpenCode an, um kostenpflichtige Go-Modelle zu erhalten. Später möglich: Zahnrad → Anbieter.");
        put("Разрешить доступ ко всем файлам", "Zugriff auf alle Dateien erlauben");
        put("Отключить оптимизацию батареи", "Akku-Optimierung deaktivieren");
        put("Начать работу", "Loslegen");
        put("Пропустить", "Überspringen");
        put("✓ доступ есть", "✓ Zugriff erlaubt");
        put("✗ доступа нет", "✗ kein Zugriff");
        put("✓ оптимизация отключена", "✓ Optimierung aus");
        put("• оптимизация включена (не критично)", "• Optimierung an (unkritisch)");
        put("Системные настройки OpenCode", "OpenCode-Systemeinstellungen");
        put("Рабочая папка (проекты)", "Arbeitsordner (Projekte)");
        put("Сохранить папку", "Ordner speichern");
        put("Лимит времени для shell-команд, сек (0 — без лимита)", "Zeitlimit für Shell-Befehle, Sek. (0 — kein Limit)");
        put("Сохранить лимит shell", "Shell-Limit speichern");
        put("Лимит вывода shell, МБ (0 — без лимита)", "Ausgabelimit der Shell, MB (0 — kein Limit)");
        put("Сохранить лимит вывода", "Ausgabelimit speichern");
        put("Проверить уведомление", "Benachrichtigung testen");
        put("Перезапустить OpenCode", "OpenCode neu starten");
        put("Показать журнал", "Protokoll anzeigen");
        put("Закрыть", "Schließen");
        put("Экспорт логов (ZIP)", "Protokolle exportieren (ZIP)");
        put("Экспорт данных (сессии, ключи)", "Daten exportieren (Sitzungen, Schlüssel)");
        put("Импорт данных из ZIP", "Daten aus ZIP importieren");
        put("Экспорт сохраняет сессии, настройки, SSH-ключи в /sdcard/Download. Импорт заменяет их данными из ZIP и перезапускает OpenCode.", "Der Export speichert Sitzungen, Einstellungen und SSH-Schlüssel in /sdcard/Download. Der Import ersetzt sie durch die ZIP-Daten und startet OpenCode neu.");
        put("Ключ OpenCode Zen и вход по аккаунту — в разделе «Провайдеры» в приложении (шестерёнка → Провайдеры).", "OpenCode-Zen-Schlüssel und Konto-Anmeldung findest du unter „Anbieter“ in der App (Zahnrad → Anbieter).");
        put("Проверить обновления OpenCode", "OpenCode-Updates prüfen");
        put("Проверить обновления приложения", "App-Updates prüfen");
        put("Канал: стабильный (нажмите для предрелизов)", "Kanal: stabil (tippen für Vorabversionen)");
        put("Канал: предрелизы (нажмите для стабильного)", "Kanal: Vorabversionen (tippen für stabil)");
        put("Канал: стабильный", "Kanal: stabil");
        put("Канал: предрелизы", "Kanal: Vorabversionen");
        put("Автопроверка обновлений приложения: вкл", "Automatische App-Update-Prüfung: an");
        put("Автопроверка обновлений приложения: выкл", "Automatische App-Update-Prüfung: aus");
        put("Проверяю обновления…", "Suche nach Updates…");
        put("Проверяю обновления приложения…", "Suche nach App-Updates…");
        put("Скачивание обновления…", "Update wird heruntergeladen…");
        put("Обновление OpenCode", "OpenCode-Update");
        put("Обновление приложения", "App-Update");
        put("Скачать", "Herunterladen");
        put("Позже", "Später");
        put("Отмена", "Abbrechen");
        put("Ошибка", "Fehler");
        put("Журнал", "Protokoll");
        put("Перезапуск…", "Neustart…");
        put("Уведомления работают", "Benachrichtigungen funktionieren");
        put("Лимит shell выключен", "Shell-Limit deaktiviert");
        put("Лимит вывода shell выключен", "Shell-Ausgabelimit deaktiviert");
        put("работает", "läuft");
        put("не запущен", "nicht gestartet");
        put("вкл", "an");
        put("выкл", "aus");
        put("есть", "ja");
        put("нет", "nein");
        put("файлов", "Dateien");
        put("Запуск…", "Start…");
        put("Подготовка…", "Vorbereitung…");
        put("Запуск OpenCode…", "OpenCode wird gestartet…");
        put("OpenCode работает", "OpenCode läuft");
        put("Ответ готов", "Antwort fertig");
        put("Ждёт ответа", "Wartet auf Antwort");
        put("Ошибка выполнения", "Ausführungsfehler");
        put("Остановлено", "Gestoppt");
        put("Стоп", "Stopp");
        put("Сообщения OpenCode", "OpenCode-Nachrichten");
        put("Запуск OpenCode… первый раз может занять до минуты", "OpenCode wird gestartet… beim ersten Mal kann es bis zu einer Minute dauern");
        put("OpenCode не запустился", "OpenCode ist nicht gestartet");
        put("Не удалось запустить", "Start fehlgeschlagen");
        put("Откройте настройки и посмотрите журнал", "Öffne die Einstellungen und sieh im Protokoll nach");
        put("Открыть настройки", "Einstellungen öffnen");
        put("Настройки", "Einstellungen");
        put("Выбрать файл", "Datei auswählen");
        put("Доступ к файлам", "Dateizugriff");
        put("OpenCode нужно читать и изменять файлы на телефоне. Разрешите «Доступ ко всем файлам» для этого приложения.", "OpenCode muss Dateien auf dem Telefon lesen und ändern. Erlaube „Zugriff auf alle Dateien“ für diese App.");
        put("Открываю чат…", "Chat wird geöffnet…");
        put("Проверка уже идёт", "Prüfung läuft bereits");
        put("Доступно обновление ", "Update verfügbar ");
        put("Обновлений нет", "Keine Updates");
        put("Установлена последняя версия ", "Neueste Version installiert ");
        put("Не удалось проверить обновления: ", "Updates konnten nicht geprüft werden: ");
        put("Уже идёт проверка или загрузка", "Prüfung oder Download läuft bereits");
        put("Разрешите установку из этого источника и нажмите «Скачать» ещё раз", "Erlaube die Installation aus dieser Quelle und tippe erneut auf „Herunterladen“");
        put("Ошибка обновления: ", "Updatefehler: ");
        put("Скачано. Подтвердите установку в системном окне.", "Heruntergeladen. Bestätige die Installation im Systemfenster.");
        put("Обновления приложения", "App-Updates");
        put("Скачивание: ", "Download: ");
        put("Обновить", "Aktualisieren");
        put("Скачать и установить?", "Herunterladen und installieren?");
        put(". Скачать и установить?", ". Herunterladen und installieren?");
        put("OpenCode ждёт вашего ответа", "OpenCode wartet auf deine Antwort");
        put("OpenCode перезапускается…", "OpenCode startet neu…");
        put("OpenCode не ответил за 90 секунд", "OpenCode hat nicht innerhalb von 90 Sekunden geantwortet");
        put("Ошибка: ", "Fehler: ");
        put("Сохранено: ", "Gespeichert: ");
        put("Логи: ", "Protokolle: ");
        put("Доступно ", "Verfügbar ");
        put(" (установлено ", " (installiert ");
        put("Обновлено. OpenCode перезапускается.", "Aktualisiert. OpenCode startet neu.");
        put("Обновление прервано", "Update abgebrochen");
        put("Ошибка экспорта логов: ", "Fehler beim Export der Protokolle: ");
        put("нет релизов v2.x", "keine v2.x-Releases");
        put("нет ассета opencode-linux-arm64-musl.tar.gz", "kein Asset opencode-linux-arm64-musl.tar.gz");
        put("нет ссылки на ассет", "kein Link zum Asset");
        put("контрольная сумма не совпала", "Prüfsumme stimmt nicht überein");
        put("скачанный файл не ELF", "heruntergeladene Datei ist kein ELF");
        put("бинарник слишком мал", "Binärdatei ist zu klein");
        put("не удалось сделать бэкап", "Backup fehlgeschlagen");
        put("не удалось заменить бинарник", "Binärdatei konnte nicht ersetzt werden");
        put("opencode не найден в архиве", "opencode im Archiv nicht gefunden");
        put("последний релиз помечен как черновик/предрелиз", "letztes Release ist als Entwurf/Vorabversion markiert");
        put("у релиза нет тега", "Release hat keinen Tag");
        put("в релизе нет APK-ассета", "Release hat kein APK-Asset");
        put("у ассета нет ссылки", "Asset hat keinen Link");
        put("нет ссылки на APK", "kein Link zum APK");
        put("не удалось создать ", "konnte nicht erstellt werden ");
        put("не удалось заменить старый APK", "altes APK konnte nicht ersetzt werden");
        put("не удалось переименовать APK", "APK konnte nicht umbenannt werden");
        put("скачанный файл не APK", "heruntergeladene Datei ist kein APK");
        put("чужой пакет: ", "fremdes Paket: ");
        put("версия в APK не новее установленной", "APK-Version ist nicht neuer als die installierte");
        put("APK подписан другим ключом — установка поверх невозможна", "APK ist mit einem anderen Schlüssel signiert — Installation darüber ist nicht möglich");
        put("файл слишком мал: ", "Datei ist zu klein: ");
        put("Shizuku не запущен", "Shizuku läuft nicht");
        put("[команда остановлена: лимит ", "[Befehl gestoppt: Limit ");
        put(" с. Изменить: ⚙ Настройки → «Лимит shell»]", " s. Ändern: ⚙ Einstellungen → „Shell-Limit“]");
        put("… (вывод обрезан, лимит ", "… (Ausgabe gekürzt, Limit ");
        put(" МБ: ⚙ Настройки → «Лимит вывода shell»)", " MB: ⚙ Einstellungen → „Shell-Ausgabelimit“)");
        sub(" · свой ключ", " · eigener Schlüssel");
        sub(" · по API-ключу", " · per API-Schlüssel");
        sub(" · подключён", " · verbunden");
        sub(" · вход по аккаунту", " · Konto-Anmeldung");
        sub(" · вход или ключ", " · Anmelden oder Schlüssel");
        sub(" · переменная окружения", " · Umgebungsvariable");
        sub(" · нет способа входа", " · keine Anmeldemöglichkeit");
        sub(" · скрыт в списке моделей", " · in der Liste ausgeblendet");
        sub("· выход $", "· Ausgabe $");
        sub("· кэш $", "· Cache $");
        sub(" за 1M", " pro 1M");
        sub(" млн токенов выхода в месяц", " Mio. Ausgabe-Token pro Monat");
        sub(" запросов за 5 часов", " Anfragen pro 5 Stunden");
        sub(" из ", " von ");
        sub("сброс ", "Zurücksetzung in ");
        sub("Загружаю: ", "Lade: ");
        sub("Загружаю ", "Lade ");
        sub("действие: ", "Aktion: ");
        sub("ресурсы:", "Ressourcen:");
        sub("Логи: ", "Protokolle: ");
        sub("Показаны первые ", "Zeige die ersten ");
        sub("Загружено: ", "Geladen: ");
        sub("С телефона: ", "Vom Telefon: ");
        sub("Не загрузилось: ", "Nicht geladen: ");
        sub("Не получилось: ", "Fehlgeschlagen: ");
        sub("Код скопирован: ", "Code kopiert: ");
        sub("вход $", "Eingabe $");
        sub(", с ошибкой: ", ", mit Fehler: ");
        sub("Ошибка входа: ", "Anmeldefehler: ");
        sub("Ошибка: ", "Fehler: ");
        sub("Код: ", "Code: ");
        sub("Модель: ", "Modell: ");
        sub("Рабочая папка: ", "Arbeitsordner: ");
        sub("Новый чат: ", "Neuer Chat: ");
        sub("Веб-поиск: ", "Websuche: ");
        sub("Ошибка запуска: ", "Startfehler: ");
        sub("Не удалось выйти: ", "Abmelden fehlgeschlagen: ");
        sub("Код не принят: ", "Code nicht akzeptiert: ");
        sub("Вход не выполнен: ", "Anmeldung nicht abgeschlossen: ");
        sub("API-ключ ", "API-Schlüssel ");
        sub("Ответ через ", "Antwort über ");
        sub("На ПК это делает ", "Am PC macht das ");
        sub("Shizuku запустится сам.", "Shizuku startet selbst.");
        sub("«Номер сборки».", "„Build-Nummer“.");
        sub("Затем «Проверить снова».", "Danach „Erneut prüfen“.");
        sub("Сохранено: ", "Gespeichert: ");
        sub("Ошибка экспорта логов: ", "Fehler beim Export der Protokolle: ");
        sub("Ошибка обновления: ", "Updatefehler: ");
        sub("Скачивание: ", "Download: ");
        sub("Порт: ", "Port: ");
        sub("OpenCode: работает", "OpenCode: läuft");
        sub("OpenCode: не запущен", "OpenCode: nicht gestartet");
        sub("Сервер: работает", "Server: läuft");
        sub("Сервер: не запущен", "Server: nicht gestartet");
        sub("Доступ к файлам: есть", "Dateizugriff: erlaubt");
        sub("Доступ к файлам: нет", "Dateizugriff: nein");
        put("Настроить", "Einrichten");
        put("Фоновая работа", "Hintergrundarbeit");
        put("Доступ к /sdcard", "Zugriff auf /sdcard");
        for (Map.Entry<String, String> e : MAP.entrySet()) {
            if (e.getKey().length() >= 14 && e.getKey().indexOf(' ') >= 0) LONG.add(e);
        }
        LONG.sort(new Comparator<Map.Entry<String, String>>() {
            @Override public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                return b.getKey().length() - a.getKey().length();
            }
        });
        SUBS.sort(new Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) {
                return b[0].length() - a[0].length();
            }
        });
    }

    private static void put(String ru, String de) { MAP.put(ru, de); }
    private static void sub(String ru, String de) { SUBS.add(new String[] { ru, de }); }

    public static String apply(String text) {
        for (String[] p : SUBS) {
            if (text.contains(p[0])) text = text.replace(p[0], p[1]);
        }
        for (Map.Entry<String, String> p : LONG) {
            if (text.contains(p.getKey())) text = text.replace(p.getKey(), p.getValue());
        }
        return text.replaceAll("(\\d+)\\s+МБ", "$1 MB")
                .replaceAll("(\\d+)\\s+КБ", "$1 KB")
                .replaceAll("(\\d+)\\s+ГБ", "$1 GB");
    }
}
