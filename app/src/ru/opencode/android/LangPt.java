package ru.opencode.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Português (Brasil) (pt). Traduzido das strings de origem em russo. */
public final class LangPt {
    public static final Map<String, String> MAP = new HashMap<String, String>();
    static final List<String[]> SUBS = new ArrayList<String[]>();
    static final List<Map.Entry<String, String>> LONG = new ArrayList<Map.Entry<String, String>>();

    static {
        put("OpenCode на телефоне", "OpenCode no seu telefone");
        put("Настоящий OpenCode работает прямо на телефоне: чаты, модели, файлы, git и инструменты. Без ПК.", "O verdadeiro OpenCode roda direto no seu telefone: chats, modelos, arquivos, git e ferramentas. Sem PC.");
        put("1. Доступ к файлам", "1. Acesso aos arquivos");
        put("2. Батарея", "2. Bateria");
        put("3. Рабочая папка", "3. Pasta de trabalho");
        put("4. Аккаунт (необязательно)", "4. Conta (opcional)");
        put("Нужен, чтобы читать и изменять файлы на телефоне.", "Necessário para ler e alterar arquivos no telefone.");
        put("Чтобы OpenCode не засыпал в фоне.", "Para o OpenCode não dormir em segundo plano.");
        put("По умолчанию — ", "Padrão — ");
        put(". Папку можно сменить в настройках.", ". A pasta pode ser alterada nas configurações.");
        put("Войдите в OpenCode, чтобы получить платные модели Go. Это можно сделать позже: шестерёнка → Провайдеры.", "Entre no OpenCode para obter os modelos pagos Go. Você pode fazer isso depois: engrenagem → Provedores.");
        put("Разрешить доступ ко всем файлам", "Permitir acesso a todos os arquivos");
        put("Отключить оптимизацию батареи", "Desativar otimização da bateria");
        put("Начать работу", "Começar");
        put("Пропустить", "Pular");
        put("✓ доступ есть", "✓ acesso concedido");
        put("✗ доступа нет", "✗ sem acesso");
        put("✓ оптимизация отключена", "✓ otimização desativada");
        put("• оптимизация включена (не критично)", "• otimização ativada (não crítico)");
        put("Системные настройки OpenCode", "Configurações do sistema do OpenCode");
        put("Рабочая папка (проекты)", "Pasta de trabalho (projetos)");
        put("Сохранить папку", "Salvar pasta");
        put("Лимит времени для shell-команд, сек (0 — без лимита)", "Limite de tempo para comandos shell, seg (0 — sem limite)");
        put("Сохранить лимит shell", "Salvar limite do shell");
        put("Лимит вывода shell, МБ (0 — без лимита)", "Limite de saída do shell, MB (0 — sem limite)");
        put("Сохранить лимит вывода", "Salvar limite de saída");
        put("Проверить уведомление", "Testar notificação");
        put("Перезапустить OpenCode", "Reiniciar o OpenCode");
        put("Показать журнал", "Mostrar registro");
        put("Закрыть", "Fechar");
        put("Экспорт логов (ZIP)", "Exportar registros (ZIP)");
        put("Экспорт данных (сессии, ключи)", "Exportar dados (sessões, chaves)");
        put("Импорт данных из ZIP", "Importar dados do ZIP");
        put("Экспорт сохраняет сессии, настройки, SSH-ключи в /sdcard/Download. Импорт заменяет их данными из ZIP и перезапускает OpenCode.", "A exportação salva sessões, configurações e chaves SSH em /sdcard/Download. A importação as substitui pelos dados do ZIP e reinicia o OpenCode.");
        put("Ключ OpenCode Zen и вход по аккаунту — в разделе «Провайдеры» в приложении (шестерёнка → Провайдеры).", "A chave OpenCode Zen e o acesso à conta ficam em «Provedores» no aplicativo (engrenagem → Provedores).");
        put("Проверить обновления OpenCode", "Verificar atualizações do OpenCode");
        put("Проверить обновления приложения", "Verificar atualizações do aplicativo");
        put("Канал: стабильный (нажмите для предрелизов)", "Canal: estável (toque para pré-lançamentos)");
        put("Канал: предрелизы (нажмите для стабильного)", "Canal: pré-lançamentos (toque para estável)");
        put("Канал: стабильный", "Canal: estável");
        put("Канал: предрелизы", "Canal: pré-lançamentos");
        put("Автопроверка обновлений приложения: вкл", "Verificação automática de atualizações do aplicativo: ativada");
        put("Автопроверка обновлений приложения: выкл", "Verificação automática de atualizações do aplicativo: desativada");
        put("Проверяю обновления…", "Verificando atualizações…");
        put("Проверяю обновления приложения…", "Verificando atualizações do aplicativo…");
        put("Скачивание обновления…", "Baixando a atualização…");
        put("Обновление OpenCode", "Atualização do OpenCode");
        put("Обновление приложения", "Atualização do aplicativo");
        put("Скачать", "Baixar");
        put("Позже", "Depois");
        put("Отмена", "Cancelar");
        put("Ошибка", "Erro");
        put("Журнал", "Registro");
        put("Перезапуск…", "Reiniciando…");
        put("Уведомления работают", "As notificações funcionam");
        put("Лимит shell выключен", "Limite do shell desativado");
        put("Лимит вывода shell выключен", "Limite de saída do shell desativado");
        put("работает", "funcionando");
        put("не запущен", "não iniciado");
        put("вкл", "ativado");
        put("выкл", "desativado");
        put("есть", "sim");
        put("нет", "não");
        put("файлов", "arquivos");
        put("Запуск…", "Iniciando…");
        put("Подготовка…", "Preparando…");
        put("Запуск OpenCode…", "Iniciando o OpenCode…");
        put("OpenCode работает", "OpenCode em execução");
        put("Ответ готов", "Resposta pronta");
        put("Ждёт ответа", "Aguardando resposta");
        put("Ошибка выполнения", "Erro de execução");
        put("Остановлено", "Interrompido");
        put("Стоп", "Parar");
        put("Сообщения OpenCode", "Mensagens do OpenCode");
        put("Запуск OpenCode… первый раз может занять до минуты", "Iniciando o OpenCode… na primeira vez pode levar até um minuto");
        put("OpenCode не запустился", "O OpenCode não iniciou");
        put("Не удалось запустить", "Não foi possível iniciar");
        put("Откройте настройки и посмотрите журнал", "Abra as configurações e veja o registro");
        put("Открыть настройки", "Abrir configurações");
        put("Настройки", "Configurações");
        put("Выбрать файл", "Escolher arquivo");
        put("Доступ к файлам", "Acesso aos arquivos");
        put("OpenCode нужно читать и изменять файлы на телефоне. Разрешите «Доступ ко всем файлам» для этого приложения.", "O OpenCode precisa ler e alterar arquivos no telefone. Permita «Acesso a todos os arquivos» para este aplicativo.");
        put("Открываю чат…", "Abrindo o chat…");
        put("Проверка уже идёт", "A verificação já está em andamento");
        put("Доступно обновление ", "Atualização disponível ");
        put("Обновлений нет", "Sem atualizações");
        put("Установлена последняя версия ", "Última versão instalada ");
        put("Не удалось проверить обновления: ", "Não foi possível verificar atualizações: ");
        put("Уже идёт проверка или загрузка", "Já há verificação ou download em andamento");
        put("Разрешите установку из этого источника и нажмите «Скачать» ещё раз", "Permita a instalação desta fonte e toque em «Baixar» novamente");
        put("Ошибка обновления: ", "Erro de atualização: ");
        put("Скачано. Подтвердите установку в системном окне.", "Baixado. Confirme a instalação na janela do sistema.");
        put("Обновления приложения", "Atualizações do aplicativo");
        put("Скачивание: ", "Baixando: ");
        put("Обновить", "Atualizar");
        put("Скачать и установить?", "Baixar e instalar?");
        put(". Скачать и установить?", ". Baixar e instalar?");
        put("OpenCode ждёт вашего ответа", "O OpenCode aguarda sua resposta");
        put("OpenCode перезапускается…", "O OpenCode está reiniciando…");
        put("OpenCode не ответил за 90 секунд", "O OpenCode não respondeu em 90 segundos");
        put("Ошибка: ", "Erro: ");
        put("Сохранено: ", "Salvo: ");
        put("Логи: ", "Registros: ");
        put("Доступно ", "Disponível ");
        put(" (установлено ", " (instalado ");
        put("Обновлено. OpenCode перезапускается.", "Atualizado. O OpenCode está reiniciando.");
        put("Обновление прервано", "Atualização interrompida");
        put("Ошибка экспорта логов: ", "Erro ao exportar registros: ");
        put("нет релизов v2.x", "sem versões v2.x");
        put("нет ассета opencode-linux-arm64-musl.tar.gz", "sem o asset opencode-linux-arm64-musl.tar.gz");
        put("нет ссылки на ассет", "sem link para o asset");
        put("контрольная сумма не совпала", "a soma de verificação não confere");
        put("скачанный файл не ELF", "o arquivo baixado não é ELF");
        put("бинарник слишком мал", "binário pequeno demais");
        put("не удалось сделать бэкап", "não foi possível fazer backup");
        put("не удалось заменить бинарник", "não foi possível substituir o binário");
        put("opencode не найден в архиве", "opencode não encontrado no arquivo");
        put("последний релиз помечен как черновик/предрелиз", "a última versão está marcada como rascunho/pré-lançamento");
        put("у релиза нет тега", "a versão não tem tag");
        put("в релизе нет APK-ассета", "a versão não tem asset APK");
        put("у ассета нет ссылки", "o asset não tem link");
        put("нет ссылки на APK", "sem link para o APK");
        put("не удалось создать ", "não foi possível criar ");
        put("не удалось заменить старый APK", "não foi possível substituir o APK antigo");
        put("не удалось переименовать APK", "não foi possível renomear o APK");
        put("скачанный файл не APK", "o arquivo baixado não é APK");
        put("чужой пакет: ", "pacote de outro aplicativo: ");
        put("версия в APK не новее установленной", "a versão do APK não é mais nova que a instalada");
        put("APK подписан другим ключом — установка поверх невозможна", "O APK está assinado com outra chave — não é possível instalar por cima");
        put("файл слишком мал: ", "arquivo pequeno demais: ");
        put("Shizuku не запущен", "Shizuku não está em execução");
        put("[команда остановлена: лимит ", "[comando interrompido: limite ");
        put(" с. Изменить: ⚙ Настройки → «Лимит shell»]", " s. Alterar: ⚙ Configurações → «Limite do shell»]");
        put("… (вывод обрезан, лимит ", "… (saída cortada, limite ");
        put(" МБ: ⚙ Настройки → «Лимит вывода shell»)", " MB: ⚙ Configurações → «Limite de saída do shell»)");
        sub(" · свой ключ", " · chave própria");
        sub(" · по API-ключу", " · por chave de API");
        sub(" · подключён", " · conectado");
        sub(" · вход по аккаунту", " · entrada na conta");
        sub(" · вход или ключ", " · entrar ou usar chave");
        sub(" · переменная окружения", " · variável de ambiente");
        sub(" · нет способа входа", " · sem forma de entrar");
        sub(" · скрыт в списке моделей", " · oculto na lista de modelos");
        sub("· выход $", "· saída $");
        sub("· кэш $", "· cache $");
        sub(" за 1M", " por 1M");
        sub(" млн токенов выхода в месяц", " M tokens de saída por mês");
        sub(" запросов за 5 часов", " solicitações a cada 5 horas");
        sub(" из ", " de ");
        sub("сброс ", "reinicia em ");
        sub("Загружаю: ", "Carregando: ");
        sub("Загружаю ", "Carregando ");
        sub("действие: ", "ação: ");
        sub("ресурсы:", "recursos:");
        sub("Логи: ", "Registros: ");
        sub("Показаны первые ", "Mostrando os primeiros ");
        sub("Загружено: ", "Carregado: ");
        sub("С телефона: ", "Do telefone: ");
        sub("Не загрузилось: ", "Não carregou: ");
        sub("Не получилось: ", "Não funcionou: ");
        sub("Код скопирован: ", "Código copiado: ");
        sub("вход $", "entrada $");
        sub(", с ошибкой: ", ", com erro: ");
        sub("Ошибка входа: ", "Erro ao entrar: ");
        sub("Ошибка: ", "Erro: ");
        sub("Код: ", "Código: ");
        sub("Модель: ", "Modelo: ");
        sub("Рабочая папка: ", "Pasta de trabalho: ");
        sub("Новый чат: ", "Novo chat: ");
        sub("Веб-поиск: ", "Busca na web: ");
        sub("Ошибка запуска: ", "Erro ao iniciar: ");
        sub("Не удалось выйти: ", "Não foi possível sair: ");
        sub("Код не принят: ", "Código não aceito: ");
        sub("Вход не выполнен: ", "Acesso não concluído: ");
        sub("API-ключ ", "Chave de API ");
        sub("Ответ через ", "Resposta via ");
        sub("На ПК это делает ", "No PC isso é feito por ");
        sub("Shizuku запустится сам.", "O Shizuku iniciará sozinho.");
        sub("«Номер сборки».", "«Número da versão».");
        sub("Затем «Проверить снова».", "Depois, «Testar novamente».");
        sub("Сохранено: ", "Salvo: ");
        sub("Ошибка экспорта логов: ", "Erro ao exportar registros: ");
        sub("Ошибка обновления: ", "Erro de atualização: ");
        sub("Скачивание: ", "Baixando: ");
        sub("Порт: ", "Porta: ");
        sub("OpenCode: работает", "OpenCode: em execução");
        sub("OpenCode: не запущен", "OpenCode: parado");
        sub("Сервер: работает", "Servidor: em execução");
        sub("Сервер: не запущен", "Servidor: parado");
        sub("Доступ к файлам: есть", "Acesso aos arquivos: concedido");
        sub("Доступ к файлам: нет", "Acesso aos arquivos: não");
        put("Настроить", "Configurar");
        put("Фоновая работа", "Trabalho em segundo plano");
        put("Доступ к /sdcard", "Acesso a /sdcard");
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

    private static void put(String ru, String pt) { MAP.put(ru, pt); }
    private static void sub(String ru, String pt) { SUBS.add(new String[] { ru, pt }); }

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
