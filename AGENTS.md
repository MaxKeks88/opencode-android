# OpenCode Android — техническая документация проекта

> Это **не** старый `OpenCode Remote` (мост к ПК). Это самостоятельное Android-приложение:
> внутри APK лежит настоящий OpenCode v2.2.6, он работает на телефоне без ПК.
> Дизайн и логика интерфейса взяты из `C:\Users\WorkPC\Мой диск\Open\page_chat.html`.

## 1. Что уже работает (проверено на телефоне)

Телефон: **VKP-NX9, Android 16 (SDK 36), arm64-v8a**, пакет `ru.opencode.android`.

- OpenCode `v2.2.6` запускается **нативно** (musl-бинарник + musl loader), без proot и без ПК.
- `opencode serve` слушает `127.0.0.1:4096` (если занят — следующий свободный порт).
- Страница `page_chat.html` открывается в WebView с локального сервера `127.0.0.1:8769`.
- Работают: список чатов, создание/переименование/удаление, выбор модели, отправка,
  остановка, стриминг текста/размышлений, инструменты `write`, `shell`, `glob`, `grep`,
  вложения (файл через `/upload` → `file://`), папки на `/sdcard`, настройки, уведомления.
- Проверено вживую: агент создал `test.txt`, выполнил `ls -la`, нашёл файлы через `glob`,
  прочитал вложение, ответил на русском.
- **Интернет у модели есть.** `webfetch` открывает URL (проверено:
  `example.com` → «Example Domain», `opencode.ai/docs`), `websearch` ищет через
  провайдера (`/api/websearch/provider` → `exa, firecrawl, parallel, tavily,
  tinyfish`; у TinyFish ключ не нужен). Shell-инструменты тоже
  в сети: `python3 -c "urllib…"` → 200, `git clone`/`pip`/`npx` — через прокси.
  Выбор провайдера и ключ — ⚙ Настройки → «Веб-поиск» (1.11).
- Токен доступа к локальному серверу: без `X-Bridge-Token` запросы к `/oc`, `/android`,
  `/files`, `/thumb`, `/upload` возвращают **403** (проверено).

## 2. Архитектура

```
┌──────────────────────────── Android-приложение (ru.opencode.android) ────────────────────────────┐
│                                                                                                  │
│  MainActivity (WebView)                                                                          │
│      │ http://127.0.0.1:8769/?v=...#token=<localToken>                                           │
│      ▼                                                                                           │
│  assets/app/page.html  +  assets/app/bridge.js   (дизайн + локальный мост)                       │
│      │ fetch/XHR/EventSource                                                                     │
│      ▼                                                                                           │
│  LocalServer.java  (127.0.0.1:8769)                                                              │
│      ├── /                → page.html                                                            │
│      ├── /app/*, /bridge.js → bridge.js                                                          │
│      ├── /ping             → {"app":"opencode-remote","ver":0}                                    │
│      ├── /oc/*             → прокси к opencode serve (Basic auth + X-Bridge-Token)                │
│      ├── /android/*        → файловая система телефона, home, config, log, notify                 │
│      ├── /upload           → multipart, файлы в files/uploads                                    │
│      ├── /files/<path>     → медиа (uploads, /sdcard/..., Pictures, Download)                      │
│      └── /thumb?path=...   → квадратные превью                                                   │
│                                                                                                  │
│  RuntimeService.java (foreground service)                                                        │
│      ├── Runtime.ensure(): распаковка assets/runtime → files/usr, files/web                       │
│      ├── killOrphanPid(): убивает opencode из files/opencode.pid                                 │
│      ├── freePort(): 4096 → 4097 → ...                                                           │
│      ├── запуск:                                                                                 │
│      │   files/usr/lib/ld-musl-aarch64.so.1 --library-path files/usr/lib \                      │
│      │       files/usr/bin/opencode serve --hostname 127.0.0.1 --port <p> --print-logs            │
│      ├── notifyLoop(): фоновые уведомления «Ответ готов» / «Ждёт ответа»                          │
│      ├── LocalServer.start()                                                                     │
│      └── ProxyServer.start(): DNS-прокси; opencode идёт через HTTPS_PROXY                       │
│                                                                                                  │
│  AndroidBridge.java (window.Android): notify, vibrate, copyText, openUrl,                        │
│      getSettings, openSettings, pickFile, requestStorage, log, home, version                     │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Почему без proot

Изначально планировался Debian + proot. На практике оказалось, что
`opencode-linux-arm64-musl` запускается на Android **напрямую** через musl loader:
Android-ядро исполняет musl-loader, а тот подгружает `libc.musl`, `libstdc++`, `libgcc`.
Это быстрее, меньше и надёжнее, чем proot. Поэтому proot в проекте нет.

Ограничение: бинарник всё равно требует запуска через loader (интерпретатор
`/lib/ld-musl-aarch64.so.1` в APK-пути отсутствует), поэтому в Java он всегда
запускается как `loader --library-path ... opencode`.

## 3. Версии и артефакты

| Что | Версия / размер | Откуда |
|---|---|---|
| OpenCode | **2.2.6**, arm64 musl, 253 631 592 байт | GitHub `anomalyco/opencode` release `v2.2.6`, asset `opencode-linux-arm64-musl.tar.gz` |
| musl loader | `ld-musl-aarch64.so.1`, 723 480 байт | Alpine minirootfs 3.24.4 aarch64 |
| libstdc++ | `libstdc++.so.6` (15.2.0-r5), 2 820 600 байт | Alpine v3.24 main aarch64 |
| libgcc_s | `libgcc_s.so.1`, 133 008 байт | Alpine v3.24 main aarch64 |
| ripgrep | 15.1.0, 3 152 312 байт + `libpcre2-8.so.0` | Alpine v3.24 community/main aarch64 |
| git | 2.54.0, 3 213 592 байт + `libpcre2-8.so.0`, `libz.so.1` | Alpine v3.24 main aarch64 |
| Python | 3.12.16 static musl, ~42 МБ | python-build-standalone `20261026` |
| APK | `OpenCodeAndroid.apk`, ~192.6 МБ | `python build_apk.py` |
| Пакет | `ru.opencode.android`, versionCode 19, versionName 1.11.0 | `app/AndroidManifest.xml` |
| minSdk / targetSdk | 26 / **28** | target 28 нужен для exec из каталога приложения (как Termux) |

Исходники OpenCode для справки: `C:\Users\WorkPC\AppData\Local\Temp\opencode-src`
(ветка `dev`). API-схема живого сервера: `openapi.json` в корне проекта
(снята с v2.2.6; можно обновить `GET /openapi.json`).

## 4. Структура проекта

```
C:\Users\WorkPC\Desktop\OpenCodeAPK\
├── AGENTS.md                     ← этот файл
├── СДЕЛАНО.md                    ← краткий русский журнал
├── build_apk.py                  ← сборщик APK без Gradle
├── build_apk.bat / install_apk.bat ← обёртки для Windows
├── keystore.jks / keystore.properties  ← подпись (не публиковать)
├── OpenCodeAndroid.apk           ← готовый APK
├── openapi.json                  ← схема API v2.2.6
├── runtime\
│   ├── opencode.gz               ← 116.6 МБ, gzip от бинарника
│   ├── rg.gz                     ← ripgrep
│   ├── git\                      ← git 2.54 + libs + builtins.txt
│   └── python\                   ← CPython 3.12 static musl + stdlib
├── assets\
│   ├── app\
│   │   ├── page.html             ← page_chat.html с правками (дизайн)
│   │   └── bridge.js             ← локальный мост (замена oc_server.py)
│   └── runtime\
│       ├── ld-musl-aarch64.so.1
│       ├── libc.musl-aarch64.so.1 ← копия loader
│       ├── libstdc++.so.6
│       ├── libgcc_s.so.1
│       └── libpcre2-8.so.0
├── app\
│   ├── AndroidManifest.xml
│   ├── res\mipmap-*\ic_launcher.png
│   └── src\ru\opencode\android\
│       ├── App.java              ← пути, prefs, пароль, localToken, лог
│       ├── MainActivity.java     ← WebView, splash, права, file chooser, CDP
│       ├── RuntimeService.java   ← foreground service, запуск/надзор opencode, уведомления
│       ├── Runtime.java          ← распаковка assets, syncWeb, обёртки rg/bash
│       ├── LocalServer.java      ← HTTP 8769, прокси /oc, /android, upload, files, thumb
│       ├── ProxyServer.java      ← DNS-прокси 127.0.0.1:<random> для opencode (musl/Bun)
│       ├── AndroidBridge.java    ← window.Android (настройки, права, уведомления)
│       ├── SettingsActivity.java ← папка, перезапуск, журнал, экспорт/импорт, обновления
│       ├── OnboardingActivity.java ← первый запуск (права, папка, модель)
│       ├── Notifications.java    ← канал уведомлений и показ
│       ├── Updater.java          ← проверка/скачивание обновлений OpenCode
│       ├── DeviceService.java    ← occtl: settings/pkg/app для агента (localhost)
│       ├── ShizukuBridge.java    ← полный доступ (shell uid 2000) через Shizuku
│       └── BootReceiver.java     ← автозапуск после перезагрузки
├── tools\
│   ├── dev_server.py             ← отладка страницы в браузере на ПК
│   ├── cdp.py                    ← управление WebView телефона через CDP
│   ├── clean_project.py          ← уборка build/ и дублей после рабочей сборки
│   └── enable_powers.bat/.sh     ← разовая выдача расширенных прав через ADB
├── libs\
│   ├── shizuku-*.jar             ← Shizuku API 13.1.5 (MIT)
│   └── README.md
```

## 5. Сборка и установка

### Сборка

```powershell
# на домашнем ПК путь содержит кириллицу («Мой диск») — aapt2 её не открывает,
# поэтому собираем через буквенный диск
subst X: "C:\Users\HomePC\Мой диск\OpenCodeAPK"
python X:\build_apk.py
```

Скрипт сам находит:
- Android SDK: `%ANDROID_SDK_ROOT%` / `%ANDROID_HOME%` / `%LOCALAPPDATA%\Android\Sdk`
- build-tools: последние (на домашнем ПК 35.0.0)
- platform: последняя `android-*` (на домашнем ПК android-34)
- JDK: `%JAVA_HOME%` (здесь Adoptium JDK 17)
- keystore: `keystore.jks` / пароль в `keystore.properties`

`BASE` в `build_apk.py` не разворачивает `subst` (`os.path.abspath`), а
`force_rmtree()` снимает read-only с каталогов перед очисткой `build`
(Google Drive помечает их «только чтение»).

Шаги: `aapt2 compile` → `aapt2 link` → `javac` → `d8` → Python zip (runtime) →
`zipalign` → `apksigner`. Выход: `OpenCodeAndroid.apk`.

Подпись: по умолчанию берётся `keystore.jks`; если его нет, создаётся
отладочный ключ с паролями `opencode2026` (сборщик печатает предупреждение).
`python build_apk.py --release` требует свой ключ: падает с ошибкой, если нет
`keystore.properties`/`keystore.jks` или остались пароли по умолчанию.

### Установка

```powershell
adb install --no-incremental -r -d OpenCodeAndroid.apk
```

На этом телефоне проверка установки отключена:
`settings put global verifier_verify_adb_installs 0` и `package_verifier_enable 0`.
Без этого Android 16 может ответить `INSTALL_FAILED_VERIFICATION_FAILURE`.
Если не ставится поверх — версия/подпись; не пересоздавай keystore без причины.

### Обновление версии

- `versionCode` и `versionName` — в `app/AndroidManifest.xml`.
- Маркер рантайма **руками не поднимается** (с 1.9.5): `build_apk.py` пишет
  `assets/runtime/manifest.txt` (SHA-256 + размер каждого файла), а
  `Runtime.ensure()` берёт `m<8 байт SHA-256 манифеста>`. Меняются бинарники —
  маркер меняется сам; меняется только версия приложения — маркер тот же,
  и 250 МБ не распаковываются. `usr/.version` хранит этот маркер.
- `syncWeb()` копирует `assets/app/*` в `files/web` при **каждом** старте,
  поэтому правки `page.html`/`bridge.js` попадают после пересборки.

## 6. Как устроен мост (bridge.js)

`bridge.js` заменяет `oc_server.py` из старого проекта. Он живёт в WebView и
общается с `opencode serve` через `/oc` (тот же origin), а не через WebSocket.

### Формат для страницы (совместим со старым `oc_server.py`)

- `handle({t:"state", ...})` — полное состояние: `chat`, `chats`, `messages`,
  `asks`, `attachments`, `hasOlder`, `usage`, `media`, `home`, `online`, `busy`.
- `handle({t:"upd", ...})` — частичное: изменившиеся `messages`, `chat`, `chats`,
  `asks`, `attachments`, `usage`, `online`, `status`, `busy`.
- `handle({t:"chats", items})`, `{t:"models", items, current}`, `{t:"fs", ...}`,
  `{t:"toast", text, kind}`, `{t:"sent"}`, `{t:"pong"}`.
- Каталог провайдеров: `{t:"catalog", items}`, `{t:"oauth", integration, status,
  url, instructions, error}`, `{t:"provbusy", integration, busy}`,
  `{t:"provmodels", integration, items}`.
- Команды страницы (`send({t:...})`): `init`, `refresh`, `open`, `new`, `chats`,
  `send`, `stop`, `rename`, `delchat`, `move`, `models`, `setmodel`, `attach`,
  `detach`, `fs`, `mkdir`, `older`, `ansperm`, `ansform`, `providers`, `usage`,
  `catalog`, `connectkey`, `connectoauth`, `oauthcode`, `disconnectprov`,
  `provmodels`, `ping`, `debug`.

### Сообщения OpenCode → формат страницы

`renderMessage()` — порт `render_message()` из `oc_server.py`:
- `type:"user"` → `{id, role:"user", text, files, ts}`
- `type:"assistant"` → `{id, role:"assistant", blocks:[...], model, finish, cost, tokens, error}`
  - `content[].type === "text"` → `{k:"text", text}`
  - `"reasoning"` → `{k:"reason", text, done}`
  - `"tool"` → `{k:"tool", name, status, hint, input, out}`
- `type:"idle"` → пропускается
- `shell` / `compaction` / `system` / `skill` / `*-switched` → `role:"info"`

`msgDone()` — определяет, закончился ли ответ (как в Python-мосте): учитывает
`time.completed` и статусы `running/streaming` у частей.

### События SSE

`connectEvents()` подписывается на `/oc/api/event?token=...`:
- `session.created/renamed/deleted` → обновить список.
- `session.execution.started` → `active.add(sid)`, `execDone=false`.
- `session.execution.succeeded/failed/interrupted` → `active.delete`, `done[sid]=...`,
  `tailRefresh()`, `syncMeta()`, `push()`, обновить чаты.
- `form.*`, `permission.*`, `session.permissions` → `refreshAsks()`.
- `session.usage.updated` → `usage={cost,tokens}`.
- `session.model.selected` → `syncMeta()`.
- `session.text.delta` / `reasoning.delta` → `schedulePush(350, tail=true)`.
- остальные для текущей сессии → `schedulePush(300, tail=true)`.

Плюс страховка: `busyPoll` каждые 1.2 с и `slowPoll` каждые 3.5 с перечитывают
хвост/активные сессии, если события потерялись.

### Модель по умолчанию

`DEFAULT_MODEL = {id:"deepseek-v4.1-flash-free", providerID:"opencode"}`.
Выбор сохраняется в `localStorage["oc_model"]`; новая сессия без модели
получает сохранённую/дефолтную через `POST /api/session/{id}/model`.

### Markdown в ответах (1.10)

`md()` (page.html) понимает заголовки, `**жирный**`, `*курсив*`, `` `код` ``,
маркированные и нумерованные списки, блоки кода. Ссылки (`http/https` и
`[текст](url)`) становятся кликабельными (`target="_blank"` → внешний
браузер через `openExternally()`); инлайн-код экранируется до ссылок, поэтому
URL внутри `` ` `` не превращается в ссылку.

### Правила агента (1.10)

`Runtime.writeDeviceGuide()` пишет `~/.config/opencode/AGENTS.md` **только если
файла нет** — правки пользователя переживают перезапуск. Рядом создаётся
`AGENTS.user.md` (тоже не перезаписывается), его путь добавляется в
`"instructions"` в `opencode.json` (существующие ключи не трогаются; если
конфиг не парсится, файл остаётся как есть).

### Каталог провайдеров (1.6)

- `GET /api/integration` — 239 провайдеров: `{id,name,methods[],connections[]}`.
  Методы: `key`, `env`, `oauth` (с `form`), `external`, `command`.
- Кэш каталога: `localStorage["oc_cat_v1"]` = `{at, items}`, TTL 24 ч.
  `loadCatalog(true)` — принудительное обновление (после подключения).
- Подключение по ключу: `POST /api/integration/{id}/connect/key {key}`.
- OAuth: `POST .../connect/oauth {methodID, answer}` → `{attemptID,url,instructions,mode}`
  → `GET .../oauth/{attemptID}` (poll) → `POST .../complete {code}`.
  `answer` — объект `{поле: значение}` из `form` метода; поля с `when`
  показываются условно (например, `enterpriseUrl` у GitHub Copilot).
- Отключение: `DELETE /api/credential/{id}` (список — `GET /api/credential`).
- После подключения/отключения `refreshProvidersAll()` обновляет каталог,
  `/api/provider` и тихо `/api/model` (`listModelsQuiet()`, без открытия листа
  моделей на странице).
- UI: блок «Популярные» (Anthropic, GitHub Copilot, OpenAI, Google,
  OpenCode Console) + «Показать всех (N)» и кнопка **⟳** (сброс кэша и
  повторный `GET /api/integration`); экран каталога с поиском, порциями по 60
  и «⟳ Обновить каталог»; карточка провайдера (`openProvider()` →
  `renderProvDetail()`), в ней — список моделей провайдера
  (`{t:"provmodels", integration}` → `{t:"provmodels", items}`) и кнопка
  «📋 Скопировать код» для OAuth-инструкций вида `Enter code: XXXX-XXXX`.
- Ключи хранятся только в OpenCode (`/api/credential`), не в prefs и не в HTML.

### Веб-поиск (1.11)

- `GET /api/websearch/provider` → `{location, data:[{id,name}]}`; провайдеры
  `exa`, `firecrawl`, `parallel`, `tavily`, `tinyfish`. `POST /api/websearch`
  `{query, providerID?}` → `{location, data:{providerID, results[]}}`.
- Активный провайдер API **не отдаёт** (только список). Он хранится в конфиге:
  `"websearch": {"provider": "<id>"|"random"}` (или `false`) в
  `~/.config/opencode/opencode.json`; OpenCode перечитывает файл на лету.
- Для записи выбора добавлен `GET/POST /android/occonfig` в `LocalServer`
  (токен обязателен): меняет только ключ `websearch`, остальные настройки
  сохраняет; запись атомарная (`.tmp` + rename).
- Ключи — через `/api/integration/{id}/connect/key`, хранятся в
  `/api/credential`. TinyFish работает без ключа.
- UI: ⚙ Настройки → вкладка «Веб-поиск» (`renderWebsearch()`): метки
  «активен»/«ключ есть», «Выбрать», «Случайный», «Автоматически», поле ключа,
  «Проверить» (результаты `POST /api/websearch` — до 3 ссылок).
  Команды моста: `websearch`, `setwebsearch`, `wskey`, `wscheck`;
  событие `websearch.updated` обновляет список.

### Файлы и вложения

- `/android/fs?path=` → `{ok,path,parent,drives,entries,shortcuts}`.
  `entries[]` = `{name,path,dir,size,img,label}`.
- `/android/mkdir?parent=&name=`.
- `/upload` (multipart) → `{ok,files:[{path,name,size}]}`; файл в `files/uploads`.
- Вложение в prompt: `{uri:"file:///...", name}`; сервер сам читает файл и
  прикладывает base64 (проверено).
- Медиа: `/files/<urlencoded path>`; `resolveMedia()` принимает абсолютный путь
  (проверяет `/sdcard`, `/storage`, app data) или ищет basename в uploads/рабочей папке.
- `/thumb?path=<abs>&w=320` — JPEG-превью.

## 7. Безопасность

- `opencode serve` и локальный HTTP-сервер слушают только `127.0.0.1`.
- Прокси `/oc` добавляет Basic auth (`opencode:<random>`), пароль в `SharedPreferences`.
- Все чувствительные маршруты (`/oc`, `/android`, `/upload`, `/files`, `/thumb`)
  требуют `X-Bridge-Token` (или `?token=`), который генерируется при первом запуске
  и передаётся странице во фрагменте URL (`#token=...`).
- Без токена — 403 (проверено).
- **WebView (1.9):** навигация разрешена только на `http://127.0.0.1:8769`
  (`isAppUrl()`: схема + host + порт). Остальные ссылки уходят в системный
  браузер (`openExternally()`), а `file:`, `javascript:`, `data:`, `about:`,
  `blob:`, `chrome:` блокируются. `setAllowFileAccess(false)` — страница и
  медиа грузятся только по http (`/files/`), `file://` в WebView не нужен.
- **Лимиты локального HTTP (1.9.1):** токен проверяется **до** чтения тела
  (`needsToken()` в `handle()`), тело ≤ 128 МБ и для `Content-Length`, и для
  chunked (иначе 413), заголовки ≤ 120 строк / 64 КБ (431). Пул —
  `ThreadPoolExecutor(4..64, SynchronousQueue)`: SSE занимает поток надолго,
  поэтому очередь не копится; при переполнении сокет закрывается.
- **Импорт бэкапа (1.9.2):** `SettingsActivity` ограничивает архив 20 000
  файлов и 512 МБ (счёт по фактически записанным байтам), поток файла
  закрывается в `finally`. Пути по-прежнему проверяются (`..` + canonical).
- **Сеть по типу сборки (1.10):** `network_security_config.xml` разрешает
  cleartext только `127.0.0.1`/`localhost` (`usesCleartextTraffic="false"`);
  WebView-отладка включается лишь при `FLAG_DEBUGGABLE`; `build_apk.py
  --release` вырезает `android:debuggable="true"` из манифеста (dev-сборка
  по-прежнему отлаживается через `run-as`/CDP).
- **`/android/occonfig` (1.11)** требует токен (как весь `/android`), принимает
  только `provider`/`disabled` и меняет в `opencode.json` лишь ключ
  `websearch`; произвольные настройки через него не записать.
- `android:debuggable="true"` — **только для разработки**. Перед «боевым» APK
  можно выключить, но тогда пропадёт `run-as`/CDP-отладка.
- `MANAGE_EXTERNAL_STORAGE` + legacy storage: приложение получает доступ к `/sdcard`.
  Пользователь должен разрешить «Доступ ко всем файлам» (или READ/WRITE на старых).
- **Расширенные права (1.4)**: `WRITE_SECURE_SETTINGS`, `DUMP`, `READ_LOGS`,
  `INTERACT_ACROSS_USERS`, `PACKAGE_USAGE_STATS` выдаются только вручную через
  `adb` (`tools/enable_powers.bat`) и дают агенту менять системные настройки,
  читать `dumpsys`/`logcat`. `DeviceService` (`occtl`) слушает только
  `127.0.0.1` и требует токен из `files/.occtl`. Для Play Store такие права
  и `debuggable` нужно убирать.

## 8. Отладка

### Логи

```powershell
adb shell run-as ru.opencode.android cat files/logs/app.log
adb shell run-as ru.opencode.android cat files/logs/opencode.log
adb logcat -s OpenCode:*
```

### WebView телефона через CDP

```powershell
$apppid = adb shell pidof ru.opencode.android
adb forward tcp:9222 localabstract:webview_devtools_remote_$apppid
python tools\cdp.py eval "JSON.stringify(window.OCBridge.state)"
python tools\cdp.py text
python tools\cdp.py shot phone.png   # работает, когда WebView видим
```

`tools/cdp.py` использует `websocket-client` (уже установлен), `suppress_origin=True`.

### Страница в браузере на ПК (без телефона)

```powershell
# 1. запустить opencode на ПК с известным паролем
$env:OPENCODE_PASSWORD='devpass'
opencode-cli.exe serve --hostname 127.0.0.1 --port 4097 --print-logs
# 2. поднять dev-сервер
python tools\dev_server.py      # http://127.0.0.1:8770/
```

`tools/dev_server.py` повторяет `/oc`, `/android/*`, `/upload`, `/files` и
позволяет быстро проверять `page.html`/`bridge.js` без сборки APK.
Токен в dev-режиме не проверяется.

### Правка страницы без пересборки APK

```powershell
adb push assets\app\page.html /data/local/tmp/page.html
adb shell run-as ru.opencode.android cp /data/local/tmp/page.html files/web/page.html
# затем reload страницы
```

Но `Runtime.syncWeb()` при следующем старте приложения перезапишет `files/web`
версией из APK — для финала нужна пересборка.

## 9. Важные нюансы (грабли)

1. **Порт 4096**. После переустановки/force-stop дочерний процесс может выжить.
   Решение: `files/opencode.pid` + `killOrphanPid()`; `freePort()` с
   `SO_REUSEADDR`; иначе приложение уедет на 4097/4098 и останется два сервера.
2. **`ps` из приложения не видит чужие процессы** (SELinux), поэтому упор на PID-файл,
   а не на `ps`/grep.
3. **204 No Content** — успех (смена папки/модели), не ошибка.
4. **UTF-8** везде. `page.html` — без BOM.
5. **`syncWeb`** перезаписывает `files/web` при каждом старте. Для dev-правок
   либо пушьте после старта, либо пересобирайте.
6. **`Runtime.ensure`** сверяет `usr/.version` с хешем
   `assets/runtime/manifest.txt` (его пишет `build_apk.py`). Руками маркер
   поднимать не нужно — изменился любой runtime-файл, маркер сменится сам.
7. **`OPENCODE_DISABLE_FFF=1`** — иначе opencode пытается загрузить glibc-модуль
   `@ff-labs/fff` и поиск файлов падает. `rg` подставляется из `files/usr/bin/rg`.
8. **Каталог моделей тянется по сети.** Флаг `OPENCODE_DISABLE_MODELS_FETCH`
   больше не ставится, чтобы после входа были доступны платные модели (Go).
9. **`shell: /system/bin/sh`** в `home/.config/opencode/opencode.json`; bash нет.
10. **`/sdcard` владелец может быть `u0_aXXX`**, но FUSE всё равно разрешает запись
    при `MANAGE_EXTERNAL_STORAGE`/legacy. Проверено.
11. **Android 16 + targetSdk 28**: установка вручную; Play Protect может ругаться.
    Это осознанный выбор ради exec из data-dir. Альтернатива — target 34 +
    бинарники в `nativeLibraryDir` (не реализовано).
12. **Кириллица в PowerShell**: не отправляйте JSON через `Invoke-RestMethod` со
    строкой без явного UTF-8 — получите `????`. В приложении этой проблемы нет
    (fetch/JSON UTF-8).
13. **Страница ждёт `/ping` с `app:"opencode-remote"`** для autoreload; сервер
    отдаёт `ver:0`, поэтому перезагрузка не срабатывает.
14. **`page_chat.html`** и `bridge.js` — это форк; оригинал лежит в
    `C:\Users\WorkPC\Мой диск\Open\page_chat.html` и `oc_server.py`
    (там же протокол и Python-реализация для сверки).
15. **DNS у opencode (musl/Bun).** На Android нет `/etc/resolv.conf`, musl уходит
    на `127.0.0.1` → `getaddrinfo EAI_AGAIN`. Поэтому `ProxyServer` слушает
    `127.0.0.1:<random>`, а opencode запускается с `HTTP(S)_PROXY` на него;
    `NO_PROXY=127.0.0.1,localhost`. Прокси резолвит имена через Android (bionic).
16. **Вход по аккаунту (OAuth device flow).** UI: ⚙ Настройки → Провайдеры →
    «Войти в аккаунт OpenCode». Шаги: `POST /api/integration/opencode/connect/oauth`
    `{"methodID":"device"}` → `url` + `mode:"auto"` → браузер → опрос
    `GET .../oauth/{attemptID}` → `POST .../complete`. Креды хранятся в opencode
    (`/api/credential`). Провайдер `opencode-go` — подписка Go, `opencode` —
    оплата по факту (без баланса вернёт 402 "Insufficient account funds").
    `listModels()` убирает дубли, оставляя `opencode-go`.
17. **Путь с кириллицей.** `aapt2` на этой машине не открывает каталоги внутри
    «Мой диск». Сборка идёт через `subst X: "<папка>"` и `python X:\build_apk.py`;
    в `build_apk.py` `BASE` не разворачивает `subst` (`os.path.abspath`), а
    `force_rmtree()` снимает read-only с каталогов перед очисткой `build`.
18. **Git.** В `files/usr/git` лежит Alpine `git` + библиотеки. Обёртка
    `files/usr/bin/git` запускает его через musl-loader и задаёт `GIT_EXEC_PATH`,
    `GIT_TEMPLATE_DIR`, `GIT_SSL_CAINFO`, `CURL_CA_BUNDLE`. Часть подкоманд git
    вызывает как внешние (`git-update-index`, `git-maintenance`, …); их обёртки
    генерируются при старте из `runtime/git/builtins.txt` в
    `files/usr/git/libexec/git-core`.
19. **Сеть git.** Включён `git-remote-http` + `libcurl`/`openssl`/`nghttp2` и др.
    Обёртки `git-remote-http`/`git-remote-https` запускают хелпер через musl-loader.
    DNS у git/musl тоже нет (`/etc/resolv.conf`), поэтому обёртка git читает
    `files/.proxy` (его пишет `ProxyServer`) и выставляет `HTTPS_PROXY`/`HTTP_PROXY`,
    если они не заданы. Проверено: `git clone https://github.com/...` работает.
20. **SSH.** В `runtime/git` лежат `ssh`, `ssh-keygen`, `scp`, `sftp`,
    `ssh-keyscan`, `nc` + libs. OpenSSH на Android не находит пользователя в
    `/etc/passwd`, поэтому SSH запускается **отдельным патченным loader-ом**
    (`runtime/git/ssh-lib/ld-musl-aarch64.so.1`: строка `/etc/passwd` заменена
    на `/sdcard/p`). Java пишет `/sdcard/p` (uid/home) при старте.
    DNS у ssh/musl тоже нет, поэтому в `~/.ssh/config` прописан
    `ProxyCommand ssh-proxy %h %p`; `ssh-proxy` читает `files/.proxy` и
    запускает `nc -X connect -x 127.0.0.1:<port>`. Проверено:
    `ssh -T git@github.com` доходит до GitHub (нужен ключ в аккаунте).
21. **Python.** В `runtime/python` — статический CPython 3.12 (musl).
    Обёртки `files/usr/bin/python3`, `python`, `pip`, `pip3` запускают его
    через отдельный loader с SONAME `libc.so` (`python/lib/ld-musl-aarch64.so.1`)
    и `PYTHONHOME`. `sitecustomize.py` подменяет `sys.executable` на обёртку
    (иначе дочерние процессы падают на `/lib/ld-musl-aarch64.so.1`) и добавляет
    pip тег `musllinux_1_1` к `1_2`, чтобы ставились готовые колёса. `pip` ходит
    через `files/.proxy`, CA — из git-бандла. Проверено: `python3 -V`,
    `pip install mcp`, `python-lsp-server`, `import mcp`, `pylsp 1.15.0`.
22. **Node/Bun.** Обёртки `node`, `bun`, `npx`, `bunx` запускают встроенный Bun
    (`BUN_BE_BUN=1` + opencode). `npx`/`bunx` — шим: `bun add` в
    `$HOME/.cache/oc-npx` + запуск `.bin` через `node` (`bun x` ломается, т.к.
    `process.execPath` = loader). Проверено: `node -e`, `bun --version`,
    `npx -y cowsay`, `npx -y @modelcontextprotocol/server-everything`.
23. **MCP.** Remote MCP встроен в OpenCode; локальные — через `npx`. В
    `opencode.json` добавлен `context7` (remote); `/api/mcp` → `connected`.
    Локальный `everything` тоже подключался. Сеть MCP идёт через прокси.
24. **Исходники Java восстановлены из APK.** 2026-11-08 папка `app/src` опустела
    при синхронизации Drive; код декомпилирован из установленного APK 1.1
    (jadx) и вручную исправлены артефакты (try/catch, `Process`, `f$0`,
    дубли `break`). Поэтому в коде остались имена вида
    `m28lambda$exportBackup$16$...`, а часть методов выглядит иначе, чем
    писалось изначально. Перед крупными правками делайте резервную копию
    `app/src` (архив: `C:\Users\WorkPC\Desktop\OpenCodeAPK-src-2026-11-08.zip`).
25. **Периодический `refresh` не должен слать полный `state`.** Иначе страница
    вызывает `renderAll()`, очищает и заново строит весь список сообщений —
    экран мигает каждые ~25–30 секунд. `refreshAll(false)` в `bridge.js` шлёт
    `push()` (только изменения), а `applyState()` пропускает `renderAll`, если
    сообщения не изменились. Полный `state` — только для `init`/открытия чата.
26. **GitHub Copilot без подписки.** Вход по OAuth проходит (кред
    `github-copilot` создаётся, провайдер получает `activation:"auto"`), но
    моделей будет **0**: OpenCode обменивает GitHub-токен на Copilot-токен
    (`api.github.com/copilot_internal/v2/token`), а без подписки Copilot этот
    запрос отдаёт **403** (проверено: `/user` → 200, план `free`). Это не баг
    приложения. В карточке подключённого провайдера теперь показывается
    предупреждение «Модели не появились…», а после подключения без моделей —
    тост «Подключено, но моделей нет — проверьте подписку или ключ».
27. **`connect/key` и BOM.** Тело запроса нельзя отправлять с UTF-8 BOM
    (PowerShell `Out-File -Encoding utf8` в PS 5.1 его добавляет): сервер
    отвечает `404 IntegrationNotFoundError`. Для проверок из консоли писать
    JSON через Python/`[IO.File]::WriteAllText` без BOM.
28. **Проверка OAuth вручную.** Устройство-flow (GitHub Copilot) требует
    ввести код на сайте; пока браузер открыт, WebView в фоне может тормозить
    таймеры, поэтому опрос продолжается после возврата в приложение. Открытый
    `<attemptID>` живёт ~10 минут, затем `expired`.

## 10. Что осталось / следующие шаги

Приоритет:
1. **Пользовательская проверка на телефоне** (разблокировать, открыть, отправить
   сообщение, свернуть/развернуть, проверить уведомление).
2. **Веб-настройки**: вкладка «Разрешения» — ✅ сделано (1.2); «Новая папка»
   под путём — ✅ сделано (1.2).
3. **Git** — ✅ сделано: встроен Alpine `git 2.54.0` (musl) с библиотеками,
   HTTPS-хелпером и обёртками подкоманд; VCS/diff/revert/снапшоты,
   `git clone/pull/push` по HTTPS и по SSH работают. `~/.gitconfig` и
   `~/.ssh/config` создаются автоматически.
4. **Python, Node/Bun и MCP** — ✅ сделано: статический CPython 3.12 (musl),
   pip, `mcp`, `python-lsp-server`; обёртки `node`/`bun`/`npx`; локальный и
   remote MCP работают.
5. **Иконка/брендинг** — ✅ иконка `>_` уже стоит; версия показывается в splash.
6. **Логи/краш-репорт** — ✅ сделано (1.3): ZIP с `app.log`, `opencode.log`,
   `device.txt` в `/sdcard/Download`; `App` пишет краши в лог.
7. **Фоновая работа**: нативный watcher уже есть; проверить, что WebView не мешает
   двойными уведомлениями.
8. **Резервная копия** сессий/ключей — ✅ экспорт/импорт ZIP в настройках.
9. **Обновление OpenCode** — ✅ есть `Updater` (проверка/скачивание с бэкапом);
   `opencode upgrade` внутри APK не работает — обновление через APK.
   С 1.9.4 сверяется SHA-256 из `digest` релиза (если поле есть), канал —
   преф `update_channel` (`stable`/`preview`), кнопка в ⚙ Настройки.
10. **Git исходников** — ✅ репозиторий вне Drive, см. раздел 13.
11. **Расширенные права** — ✅ 1.4: одноразовый `tools/enable_powers.bat`,
    `occtl`, обёртки `settings`/`pm`; агент меняет системные настройки,
    читает `dumpsys`/`logcat`, открывает удаление приложений.
12. **Shizuku** — ✅ 1.5: полный shell uid 2000, `ocsh`, автоподмена `pm/input/am/cmd`,
    экран «Полные разрешения» с инструкциями. ✅ 2026-11-08 проверено после
    перезагрузки: приложение и opencode поднимаются сами (`BootReceiver`),
    токен и префы сохраняются, DeviceService (`settings`/`pm`) работает без
    Shizuku, а сам Shizuku сбрасывается — `ocsh` и экран статуса честно пишут
    «Полный доступ (Shizuku) не включён».
13. **Уведомления** — ✅ 1.6: одно уведомление на полный ответ
    (`Notifications` проверяет преф `notify`, фиксированный ID 1001;
    `RuntimeService` — debounce 1.8 с + проверка `time.idle >= time.busy`,
    дедуп запросов; дубли `BR.notify` со страницы убраны).
14. **Каталог провайдеров** — ✅ 1.6: блок «Популярные», «Показать всех
    провайдеров» с поиском/пагинацией, подключение по ключу и OAuth,
    отключение, кэш каталога 24 ч. См. раздел 6 «Каталог провайдеров».

## 11. Быстрый чек-лист после изменений

```powershell
python build_apk.py
adb install --no-incremental -r -d OpenCodeAndroid.apk
adb shell am force-stop ru.opencode.android
adb shell am start -n ru.opencode.android/.MainActivity
adb shell run-as ru.opencode.android cat files/logs/app.log
# в логе должно быть: порт OpenCode: 4096, локальный сервер: http://127.0.0.1:8769
python tools\clean_project.py --yes   # убрать build/ и старые копии APK
```

Автопроверка (телефон должен быть подключён):

```powershell
python tools\smoke_test.py          # 8 быстрых проверок: ping, токен, лимиты, каталог, модели
python tools\smoke_test.py --deep   # + сообщение, ответ и уведомление (кладёт приложение в фон)
```

Скрипт сам берёт серийник и токен из `shared_prefs`, ничего не хранит в repo.

Проверить:
- `http://127.0.0.1:8769/ping` (через `adb forward`) → `online:true`.
- `/oc/api/info` без токена → 403, с `X-Bridge-Token` → 200.
- В WebView: шапка с папкой, список чатов, модель `deepseek-v4.1-flash-free`.
- Отправить сообщение → появляется ответ, инструменты, кнопка «стоп» работает.
- Свернуть → приходит уведомление «Ответ готов» (нативный watcher).

## 12. Уборка после рабочей сборки (правило)

После того как сборка **установлена на телефон и проверена** (страница,
`/ping`, `/oc`, отправка сообщения), проект нужно подчистить, чтобы не
копить дубли и старые APK.

**Всегда оставлять:**
- `app/`, `assets/`, `runtime/`, `tools/` — исходники и рантайм;
- `build_apk.py`, `build_apk.bat`, `install_apk.bat`;
- `keystore.jks`, `keystore.properties`;
- `AGENTS.md`, `СДЕЛАНО.md`, `ПЛАН.md`, `ЗАДАЧИ.md`, `README.md`, `openapi.json`;
- **ровно один** `OpenCodeAndroid.apk` — последний рабочий.

**Удалять:**
- `build/` — промежуточные `base.apk`/`unsigned.apk`/`aligned.apk` (следующая
  сборка создаст заново);
- `build (1)`, `build (2)`, … — конфликтные копии Google Drive;
- `*.idsig` — подписи старых APK;
- `OpenCodeAndroid (1).apk`, `OpenCodeAndroid (2).apk`, … — старые копии;
- файлы/папки вида `имя (1).ext` — конфликтные копии Drive;
- `__pycache__/` — кэш Python;
- разовые тестовые файлы (`screen1.png`, `win.xml`, `upload_test.txt`,
  `events.txt`, `msgs.json`, `demo/`, `tools/patch_*.py`, `tools/utf8_test.py`).

**Команда (сначала просмотр, потом удаление):**

```powershell
python tools\clean_project.py          # что будет удалено (dry-run)
python tools\clean_project.py --yes    # удалить
```

Скрипт никогда не трогает `app/`, `assets/`, `runtime/`, `tools/` и
`OpenCodeAndroid.apk`. После уборки повторный запуск должен написать
«Нечего удалять».

Правило для агента: **собрал → установил → проверил → запустил уборку**.
Не оставляй в папке больше одного APK и промежуточные `build*`.

## 13. Git исходников (вне Google Drive)

Чтобы исходники снова не пропали при синхронизации Drive, в проекте есть
локальный репозиторий, **git-каталог вынесен за пределы Drive**:

```
worktree: C:\Users\WorkPC\Мой диск\OpenCodeAPK
git-dir:  C:\Users\WorkPC\OpenCodeAPK-src.git   (файл .git в проекте — ссылка)
```

- Отслеживаются: `app/`, `assets/`, `tools/`, скрипты сборки, документация,
  `.gitignore`.
- **Не** отслеживаются: `runtime/` (300 МБ сторонних бинарников),
  `OpenCodeAndroid.apk`, `build*`, `*.idsig`, `keystore.jks`,
  `keystore.properties`, `__pycache__/`.
- Секреты (keystore) намеренно не в git.

Команды:

```powershell
cd "C:\Users\WorkPC\Мой диск\OpenCodeAPK"
git status
git add -A
git commit -m "что изменили"
git log --oneline -5
```

При переносе проекта на другой ПК git-dir нужно скопировать отдельно
(он лежит рядом с папкой проекта, не внутри неё).

## 14. Расширенные права и occtl (1.4)

### Почему обычных разрешений недостаточно

Android не даёт приложению shell-права (uid 2000) или root (uid 0) через
манифест. Даже с `MANAGE_EXTERNAL_STORAGE` процесс остаётся
`untrusted_app` и не может:

- удалять/отключать чужие приложения (`DELETE_PACKAGES`,
  `CHANGE_COMPONENT_ENABLED_STATE`);
- отзывать runtime-разрешения (`REVOKE_RUNTIME_PERMISSIONS`);
- выполнять `input`, `am start`, `pm disable-user`;
- читать данные других приложений.

`WRITE_SECURE_SETTINGS`, `DUMP`, `READ_LOGS`, `INTERACT_ACROSS_USERS`,
`PACKAGE_USAGE_STATS` — **development**-разрешения: их можно выдать один раз
с ПК через `adb`, и они сохраняются до переустановки.

### Что сделано в 1.4

1. В манифест добавлены development-разрешения.
2. `tools/enable_powers.bat` (Windows) / `.sh` (Linux/macOS) выдают их:
   ```powershell
   tools\enable_powers.bat
   ```
3. Добавлен `DeviceService` — локальный сервер на `127.0.0.1` (токен в
   `files/.occtl`). Клиент — `occtl` в `files/usr/bin`:
   ```
   occtl settings get|put|delete system|secure|global KEY [VALUE]
   occtl pkg list [-3] | occtl pkg path PACKAGE
   occtl app details|uninstall|notifications|usage PACKAGE
   occtl toast "текст" | occtl notify "заголовок" "текст"
   occtl info
   ```
4. В `files/usr/bin` добавлены обёртки `settings` и `pm` (PATH ставит их
   раньше `/system/bin`). Поэтому существующие команды агента работают:
   - `settings get/put/delete ...` → пишет через API приложения;
   - `pm list packages` → список пакетов;
   - `pm uninstall PACKAGE` → системный диалог удаления;
   - `pm path PACKAGE` → путь к APK.
5. Глобальный `~/.config/opencode/AGENTS.md` объясняет агенту, что доступно,
   а что требует Shizuku/root.

### Что теперь реально может агент

- читать и **менять** `system/secure/global` настройки (включая отключение
  ADB, accessibility, notification listeners — если вендор не запретил);
- `dumpsys`, `logcat`, списки пакетов, пути APK;
- открыть экран удаления/настроек приложения (пользователь подтверждает);
- тосты/уведомления.

### Что всё ещё требует Shizuku или root

- тихое удаление/отключение приложений (`pm uninstall/disable-user`);
- отзыв runtime-разрешений (`pm revoke`);
- инъекция ввода (`input`), автоматизация UI;
- чтение приватных данных чужих приложений;
- `pm install` без диалога.

План на будущее: интеграция **Shizuku** (пользователь один раз запускает его
через ADB/беспроводную отладку) — тогда приложение получает настоящий shell
uid 2000 и перечисленные операции становятся доступны без ПК.

## 15. Полные разрешения: Shizuku (1.5)

### Что это

Кнопка **⚙ Настройки → Разрешения → «Полные разрешения (shell)»** в самом низу
открывает полноэкранный экран. Сверху — жёлтое предупреждение: после
перезагрузки доступ сбрасывается и всё делается на свой страх и риск. Ниже —
статус, кнопка «Что надо делать» и пошаговая инструкция двумя путями:
**с ПК** и **без ПК (Wi‑Fi + Shizuku)**. У каждого шага есть описание и
кнопки (открыть настройки разработчика, Shizuku, скопировать ADB-команду).

### Как устроено

- `libs/shizuku-*.jar` — Shizuku API 13.1.5 (MIT), подключены к `javac`/`d8`.
- В манифесте: `ShizukuProvider` с authority `ru.opencode.android.shizuku`
  и `android:permission="android.permission.INTERACT_ACROSS_USERS_FULL"`,
  meta-data `moe.shizuku.client.V3_SUPPORT=true`.
- `ShizukuBridge`: `pingBinder`, `checkSelfPermission`, `requestPermission`,
  `exec` (через `IShizukuService.newProcess` + `IRemoteProcess`).
- `DeviceService` команды `sh-ready` и `sh`; клиент `occtl sh`.
- В `files/usr/bin` появились `ocsh`, а `pm/input/am/cmd` при готовом Shizuku
  автоматически уходят в `ocsh`; без него — fallback (для `pm uninstall` —
  системный диалог).
- Если `cwd` — `/data/...` (недоступно shell), выполнение идёт из `/sdcard`.

### Запуск Shizuku (важно)

- **Shizuku 13.6+**: стартовый файл — `lib/arm64-v8a/libshizuku.so` из APK
  Shizuku. Команда (её показывает и сам Shizuku):
  ```sh
  APK=$(pm path moe.shizuku.privileged.api | sed 's/package://')
  DIR=$(dirname $APK)
  cp $DIR/lib/arm64/libshizuku.so /data/local/tmp/shizuku
  chmod 755 /data/local/tmp/shizuku
  /data/local/tmp/shizuku
  ```
  `tools/enable_powers.bat` делает это автоматически.
- **Shizuku 11.2–13.5**: `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`.
- **Без ПК**: «Отладка по Wi‑Fi» → «Сопряжение с помощью кода» → код в Shizuku
  → «Запустить». Сопряжение нужно один раз; после перезагрузки запуск
  повторяется (в 13.6+ есть автозапуск на доверенном Wi‑Fi).

### Что даёт

`uid=2000(shell)`: `pm uninstall/disable-user/revoke/grant/install`,
`input`, `am`, `cmd`, `appops`, установка APK без диалога, чтение системных
данных. Не даёт root-операций (system-раздел, SELinux, данные чужих
приложений на уровне ядра).

### Проверено на телефоне

- Shizuku 13.6 установлен, сервер запущен (pid `shizuku_server`, uid shell).
- `BR.shizukuStatus()` → `installed:true, running:true, granted:true, uid:2000`.
- `ocsh id` → `uid=2000(shell)`.
- Агент в приложении выполнил `ocsh id` и `ocsh pm list packages -3 | wc -l`
  (56 пакетов).
- **Лимиты `ocsh` (1.9.3):** вывод ≤ 4 МБ на поток с пометкой «… вывод
  обрезан, лимит 4 МБ»; таймаут — ⚙ Настройки → «Лимит времени для
  shell-команд, сек» (преф `ocsh_timeout`, 0 = без лимита). Проверено:
  10 МБ `base64` → 4 194 356 байт с пометкой; лимит 5 с → `sleep 40`
  остановлен за 5.2 с; лимит 0 → `sleep 8` отработал полностью.
- **После перезагрузки (2026-11-08):** приложение стартует само (22:37),
  opencode и локальный сервер поднимаются, токен из prefs тот же.
  `BR.shizukuStatus()` → `running:false, granted:false, uid:-1` — Shizuku
  нужно запустить заново; `ocsh`/`occtl sh-ready` отвечают «Полный доступ
  (Shizuku) не включён». `pm list packages -3` через обёртку всё равно
  работает (fallback на DeviceService, 55 пакетов), `settings get global
  adb_enabled` → `1`. Ответ на сообщение и уведомление «Ответ готов» после
  перезагрузки проверены.
