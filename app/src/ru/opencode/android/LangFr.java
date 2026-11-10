package ru.opencode.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Français (fr). Traduit des chaînes sources russes. */
public final class LangFr {
    public static final Map<String, String> MAP = new HashMap<String, String>();
    static final List<String[]> SUBS = new ArrayList<String[]>();
    static final List<Map.Entry<String, String>> LONG = new ArrayList<Map.Entry<String, String>>();

    static {
        put("OpenCode на телефоне", "OpenCode sur ton téléphone");
        put("Настоящий OpenCode работает прямо на телефоне: чаты, модели, файлы, git и инструменты. Без ПК.", "Le vrai OpenCode fonctionne directement sur le téléphone : discussions, modèles, fichiers, git et outils. Sans PC.");
        put("1. Доступ к файлам", "1. Accès aux fichiers");
        put("2. Батарея", "2. Batterie");
        put("3. Рабочая папка", "3. Dossier de travail");
        put("4. Аккаунт (необязательно)", "4. Compte (facultatif)");
        put("Нужен, чтобы читать и изменять файлы на телефоне.", "Nécessaire pour lire et modifier les fichiers du téléphone.");
        put("Чтобы OpenCode не засыпал в фоне.", "Pour qu'OpenCode ne s'endorme pas en arrière-plan.");
        put("По умолчанию — ", "Par défaut — ");
        put(". Папку можно сменить в настройках.", ". Le dossier se change dans les paramètres.");
        put("Войдите в OpenCode, чтобы получить платные модели Go. Это можно сделать позже: шестерёнка → Провайдеры.", "Connecte-toi à OpenCode pour obtenir les modèles Go payants. Plus tard : engrenage → Fournisseurs.");
        put("Разрешить доступ ко всем файлам", "Autoriser l'accès à tous les fichiers");
        put("Отключить оптимизацию батареи", "Désactiver l'optimisation de la batterie");
        put("Начать работу", "Commencer");
        put("Пропустить", "Passer");
        put("✓ доступ есть", "✓ accès accordé");
        put("✗ доступа нет", "✗ pas d'accès");
        put("✓ оптимизация отключена", "✓ optimisation désactivée");
        put("• оптимизация включена (не критично)", "• optimisation activée (non critique)");
        put("Системные настройки OpenCode", "Réglages système d'OpenCode");
        put("Рабочая папка (проекты)", "Dossier de travail (projets)");
        put("Сохранить папку", "Enregistrer le dossier");
        put("Лимит времени для shell-команд, сек (0 — без лимита)", "Limite de temps des commandes shell, s (0 — sans limite)");
        put("Сохранить лимит shell", "Enregistrer la limite shell");
        put("Лимит вывода shell, МБ (0 — без лимита)", "Limite de sortie du shell, Mo (0 — sans limite)");
        put("Сохранить лимит вывода", "Enregistrer la limite de sortie");
        put("Проверить уведомление", "Tester la notification");
        put("Перезапустить OpenCode", "Redémarrer OpenCode");
        put("Показать журнал", "Afficher le journal");
        put("Закрыть", "Fermer");
        put("Экспорт логов (ZIP)", "Exporter les journaux (ZIP)");
        put("Экспорт данных (сессии, ключи)", "Exporter les données (sessions, clés)");
        put("Импорт данных из ZIP", "Importer des données depuis un ZIP");
        put("Экспорт сохраняет сессии, настройки, SSH-ключи в /sdcard/Download. Импорт заменяет их данными из ZIP и перезапускает OpenCode.", "L'export enregistre les sessions, réglages et clés SSH dans /sdcard/Download. L'import les remplace par les données du ZIP et redémarre OpenCode.");
        put("Ключ OpenCode Zen и вход по аккаунту — в разделе «Провайдеры» в приложении (шестерёнка → Провайдеры).", "La clé OpenCode Zen et la connexion au compte se trouvent dans « Fournisseurs » (engrenage → Fournisseurs).");
        put("Проверить обновления OpenCode", "Vérifier les mises à jour d'OpenCode");
        put("Проверить обновления приложения", "Vérifier les mises à jour de l'application");
        put("Канал: стабильный (нажмите для предрелизов)", "Canal : stable (appuyer pour les préversions)");
        put("Канал: предрелизы (нажмите для стабильного)", "Canal : préversions (appuyer pour stable)");
        put("Канал: стабильный", "Canal : stable");
        put("Канал: предрелизы", "Canal : préversions");
        put("Автопроверка обновлений приложения: вкл", "Vérification automatique des mises à jour : activée");
        put("Автопроверка обновлений приложения: выкл", "Vérification automatique des mises à jour : désactivée");
        put("Проверяю обновления…", "Recherche de mises à jour…");
        put("Проверяю обновления приложения…", "Recherche de mises à jour de l'application…");
        put("Скачивание обновления…", "Téléchargement de la mise à jour…");
        put("Обновление OpenCode", "Mise à jour d'OpenCode");
        put("Обновление приложения", "Mise à jour de l'application");
        put("Скачать", "Télécharger");
        put("Позже", "Plus tard");
        put("Отмена", "Annuler");
        put("Ошибка", "Erreur");
        put("Журнал", "Journal");
        put("Перезапуск…", "Redémarrage…");
        put("Уведомления работают", "Les notifications fonctionnent");
        put("Лимит shell выключен", "Limite shell désactivée");
        put("Лимит вывода shell выключен", "Limite de sortie du shell désactivée");
        put("работает", "en marche");
        put("не запущен", "non démarré");
        put("вкл", "activé");
        put("выкл", "désactivé");
        put("есть", "oui");
        put("нет", "non");
        put("файлов", "fichiers");
        put("Запуск…", "Démarrage…");
        put("Подготовка…", "Préparation…");
        put("Запуск OpenCode…", "Démarrage d'OpenCode…");
        put("OpenCode работает", "OpenCode fonctionne");
        put("Ответ готов", "Réponse prête");
        put("Ждёт ответа", "En attente de réponse");
        put("Ошибка выполнения", "Erreur d'exécution");
        put("Остановлено", "Arrêté");
        put("Стоп", "Stop");
        put("Сообщения OpenCode", "Messages OpenCode");
        put("Запуск OpenCode… первый раз может занять до минуты", "Démarrage d'OpenCode… la première fois peut prendre une minute");
        put("OpenCode не запустился", "OpenCode n'a pas démarré");
        put("Не удалось запустить", "Impossible de démarrer");
        put("Откройте настройки и посмотрите журнал", "Ouvre les paramètres et consulte le journal");
        put("Открыть настройки", "Ouvrir les paramètres");
        put("Настройки", "Paramètres");
        put("Выбрать файл", "Choisir un fichier");
        put("Доступ к файлам", "Accès aux fichiers");
        put("OpenCode нужно читать и изменять файлы на телефоне. Разрешите «Доступ ко всем файлам» для этого приложения.", "OpenCode doit lire et modifier des fichiers sur le téléphone. Autorise « Accès à tous les fichiers » pour cette application.");
        put("Открываю чат…", "Ouverture de la discussion…");
        put("Проверка уже идёт", "Vérification déjà en cours");
        put("Доступно обновление ", "Mise à jour disponible ");
        put("Обновлений нет", "Aucune mise à jour");
        put("Установлена последняя версия ", "Dernière version installée ");
        put("Не удалось проверить обновления: ", "Impossible de vérifier les mises à jour : ");
        put("Уже идёт проверка или загрузка", "Vérification ou téléchargement déjà en cours");
        put("Разрешите установку из этого источника и нажмите «Скачать» ещё раз", "Autorise l'installation depuis cette source et appuie à nouveau sur « Télécharger »");
        put("Ошибка обновления: ", "Erreur de mise à jour : ");
        put("Скачано. Подтвердите установку в системном окне.", "Téléchargé. Confirme l'installation dans la fenêtre système.");
        put("Обновления приложения", "Mises à jour de l'application");
        put("Скачивание: ", "Téléchargement : ");
        put("Обновить", "Actualiser");
        put("Скачать и установить?", "Télécharger et installer ?");
        put(". Скачать и установить?", ". Télécharger et installer ?");
        put("OpenCode ждёт вашего ответа", "OpenCode attend ta réponse");
        put("OpenCode перезапускается…", "OpenCode redémarre…");
        put("OpenCode не ответил за 90 секунд", "OpenCode n'a pas répondu en 90 secondes");
        put("Ошибка: ", "Erreur : ");
        put("Сохранено: ", "Enregistré : ");
        put("Логи: ", "Journaux : ");
        put("Доступно ", "Disponible ");
        put(" (установлено ", " (installé ");
        put("Обновлено. OpenCode перезапускается.", "Mis à jour. OpenCode redémarre.");
        put("Обновление прервано", "Mise à jour interrompue");
        put("Ошибка экспорта логов: ", "Erreur d'export des journaux : ");
        put("нет релизов v2.x", "aucune version v2.x");
        put("нет ассета opencode-linux-arm64-musl.tar.gz", "aucun asset opencode-linux-arm64-musl.tar.gz");
        put("нет ссылки на ассет", "aucun lien vers l'asset");
        put("контрольная сумма не совпала", "la somme de contrôle ne correspond pas");
        put("скачанный файл не ELF", "le fichier téléchargé n'est pas ELF");
        put("бинарник слишком мал", "binaire trop petit");
        put("не удалось сделать бэкап", "impossible de faire une sauvegarde");
        put("не удалось заменить бинарник", "impossible de remplacer le binaire");
        put("opencode не найден в архиве", "opencode introuvable dans l'archive");
        put("последний релиз помечен как черновик/предрелиз", "la dernière version est marquée comme brouillon/préversion");
        put("у релиза нет тега", "la version n'a pas de tag");
        put("в релизе нет APK-ассета", "la version n'a pas d'asset APK");
        put("у ассета нет ссылки", "l'asset n'a pas de lien");
        put("нет ссылки на APK", "aucun lien vers l'APK");
        put("не удалось создать ", "impossible de créer ");
        put("не удалось заменить старый APK", "impossible de remplacer l'ancien APK");
        put("не удалось переименовать APK", "impossible de renommer l'APK");
        put("скачанный файл не APK", "le fichier téléchargé n'est pas un APK");
        put("чужой пакет: ", "paquet d'une autre application : ");
        put("версия в APK не новее установленной", "la version de l'APK n'est pas plus récente que l'installée");
        put("APK подписан другим ключом — установка поверх невозможна", "L'APK est signé avec une autre clé — installation par-dessus impossible");
        put("файл слишком мал: ", "fichier trop petit : ");
        put("Shizuku не запущен", "Shizuku n'est pas démarré");
        put("[команда остановлена: лимит ", "[commande arrêtée : limite ");
        put(" с. Изменить: ⚙ Настройки → «Лимит shell»]", " s. Modifier : ⚙ Paramètres → « Limite shell »]");
        put("… (вывод обрезан, лимит ", "… (sortie tronquée, limite ");
        put(" МБ: ⚙ Настройки → «Лимит вывода shell»)", " Mo : ⚙ Paramètres → « Limite de sortie du shell »)");
        sub(" · свой ключ", " · clé personnelle");
        sub(" · по API-ключу", " · par clé API");
        sub(" · подключён", " · connecté");
        sub(" · вход по аккаунту", " · connexion au compte");
        sub(" · вход или ключ", " · connexion ou clé");
        sub(" · переменная окружения", " · variable d'environnement");
        sub(" · нет способа входа", " · aucun moyen de connexion");
        sub(" · скрыт в списке моделей", " · masqué dans la liste");
        sub("· выход $", "· sortie $");
        sub("· кэш $", "· cache $");
        sub(" за 1M", " pour 1M");
        sub(" млн токенов выхода в месяц", " M tokens de sortie par mois");
        sub(" запросов за 5 часов", " requêtes par 5 heures");
        sub(" из ", " sur ");
        sub("сброс ", "réinitialisation dans ");
        sub("Загружаю: ", "Chargement : ");
        sub("Загружаю ", "Chargement ");
        sub("действие: ", "action : ");
        sub("ресурсы:", "ressources :");
        sub("Логи: ", "Journaux : ");
        sub("Показаны первые ", "Affichage des premiers ");
        sub("Загружено: ", "Chargé : ");
        sub("С телефона: ", "Depuis le téléphone : ");
        sub("Не загрузилось: ", "Non chargé : ");
        sub("Не получилось: ", "Échec : ");
        sub("Код скопирован: ", "Code copié : ");
        sub("вход $", "entrée $");
        sub(", с ошибкой: ", ", avec erreur : ");
        sub("Ошибка входа: ", "Erreur de connexion : ");
        sub("Ошибка: ", "Erreur : ");
        sub("Код: ", "Code : ");
        sub("Модель: ", "Modèle : ");
        sub("Рабочая папка: ", "Dossier de travail : ");
        sub("Новый чат: ", "Nouvelle discussion : ");
        sub("Веб-поиск: ", "Recherche web : ");
        sub("Ошибка запуска: ", "Erreur de démarrage : ");
        sub("Не удалось выйти: ", "Impossible de se déconnecter : ");
        sub("Код не принят: ", "Code refusé : ");
        sub("Вход не выполнен: ", "Connexion non effectuée : ");
        sub("API-ключ ", "Clé API ");
        sub("Ответ через ", "Réponse via ");
        sub("На ПК это делает ", "Sur PC, cela se fait avec ");
        sub("Shizuku запустится сам.", "Shizuku démarrera tout seul.");
        sub("«Номер сборки».", "« Numéro de build ».");
        sub("Затем «Проверить снова».", "Puis « Vérifier à nouveau ».");
        sub("Сохранено: ", "Enregistré : ");
        sub("Ошибка экспорта логов: ", "Erreur d'export des journaux : ");
        sub("Ошибка обновления: ", "Erreur de mise à jour : ");
        sub("Скачивание: ", "Téléchargement : ");
        sub("Порт: ", "Port : ");
        sub("OpenCode: работает", "OpenCode : en marche");
        sub("OpenCode: не запущен", "OpenCode : non démarré");
        sub("Сервер: работает", "Serveur : en marche");
        sub("Сервер: не запущен", "Serveur : non démarré");
        sub("Доступ к файлам: есть", "Accès aux fichiers : accordé");
        sub("Доступ к файлам: нет", "Accès aux fichiers : non");
        put("Настроить", "Configurer");
        put("Фоновая работа", "Travail en arrière-plan");
        put("Доступ к /sdcard", "Accès à /sdcard");
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

    private static void put(String ru, String fr) { MAP.put(ru, fr); }
    private static void sub(String ru, String fr) { SUBS.add(new String[] { ru, fr }); }

    public static String apply(String text) {
        for (String[] p : SUBS) {
            if (text.contains(p[0])) text = text.replace(p[0], p[1]);
        }
        for (Map.Entry<String, String> p : LONG) {
            if (text.contains(p.getKey())) text = text.replace(p.getKey(), p.getValue());
        }
        return text.replaceAll("(\\d+)\\s+МБ", "$1 Mo")
                .replaceAll("(\\d+)\\s+КБ", "$1 Ko")
                .replaceAll("(\\d+)\\s+ГБ", "$1 Go");
    }
}
