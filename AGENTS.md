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
| APK | `OpenCodeAndroid.apk`, ~192.4 МБ | `python build_apk.py` |
| Пакет | `ru.opencode.android`, versionCode 3, versionName 1.2 | `app/AndroidManifest.xml` |
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
│       └── BootReceiver.java     ← автозапуск после перезагрузки
├── tools\
│   ├── dev_server.py             ← отладка страницы в браузере на ПК
│   ├── cdp.py                    ← управление WebView телефона через CDP
│   └── clean_project.py          ← уборка build/ и дублей после рабочей сборки
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
- Если менялись **runtime-файлы** (`opencode`, musl, rg) — подними маркер в
  `Runtime.ensure()`: `String version = appVersion(ctx) + "-r1";` → `-r2`.
  Иначе старый распакованный бинарник не обновится.
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
- Команды страницы (`send({t:...})`): `init`, `refresh`, `open`, `new`, `chats`,
  `send`, `stop`, `rename`, `delchat`, `move`, `models`, `setmodel`, `attach`,
  `detach`, `fs`, `mkdir`, `older`, `ansperm`, `ansform`, `providers`, `usage`,
  `ping`, `debug`.

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
- `android:debuggable="true"` — **только для разработки**. Перед «боевым» APK
  можно выключить, но тогда пропадёт `run-as`/CDP-отладка.
- `MANAGE_EXTERNAL_STORAGE` + legacy storage: приложение получает доступ к `/sdcard`.
  Пользователь должен разрешить «Доступ ко всем файлам» (или READ/WRITE на старых).

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
6. **`Runtime.ensure`** проверяет `usr/.version`; при замене бинарника поднимите
   `-r1` → `-r2`.
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
5. **Иконка/брендинг**: сейчас иконка от старого OpenCode Remote.
6. **Экспорт логов/краш-репорт** и нормальный onboarding (первый запуск, права).
7. **Фоновая работа**: нативный watcher уже есть; проверить, что WebView не мешает
   двойными уведомлениями.
8. **Резервная копия** `files/home/.local/share/opencode` (сессии/ключи).
9. **Обновление OpenCode**: `opencode upgrade` внутри APK не работает; обновление —
   замена `runtime/opencode.gz` + пересборка + bump маркера.

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
