# libs

Библиотеки, которые подключаются к сборке напрямую (`javac -classpath` + `d8`).

## Shizuku API 13.1.5

- Источник: Maven Central, `dev.rikka.shizuku:{api,provider,shared,aidl}:13.1.5`
  (AAR; здесь лежат распакованные `classes.jar`).
- Лицензия: MIT, https://github.com/RikkaApps/Shizuku-API
- Зачем: `Shizuku`/`ShizukuProvider` дают приложению shell-права (uid 2000)
  после разового запуска Shizuku через ADB или беспроводную отладку.
- Файлы:
  - `shizuku-api.jar` — `rikka.shizuku.Shizuku`, `ShizukuBinderWrapper` и т.д.
  - `shizuku-aidl.jar` — `moe.shizuku.server.IShizukuService`, `IRemoteProcess`.
  - `shizuku-provider.jar` — `rikka.shizuku.ShizukuProvider` (ContentProvider).
  - `shizuku-shared.jar` — общие классы.

Провайдер объявлен в `app/AndroidManifest.xml` с authority
`ru.opencode.android.shizuku` и permission `moe.shizuku.manager.permission.API_V23`.
