# OpenCode Android

Самостоятельное Android-приложение: настоящий OpenCode v2.2.6 внутри APK,
без ПК, без моста и без proot. Дизайн — из проекта `C:\Users\WorkPC\Мой диск\Open`.

## Быстрый старт

1. `OpenCodeAndroid.apk` — установить на телефон.
2. Открыть **OpenCode**, разрешить доступ к файлам.
3. Рабочая папка по умолчанию: `/storage/emulated/0/OpenCode`.
4. Модель — кнопка внизу экрана. Бесплатные модели OpenCode Zen работают без ключа.
5. Платные модели (подписка OpenCode Go): шестерёнка в шапке → **Провайдеры** →
   **«Войти в аккаунт OpenCode»**. Откроется браузер; после входа модели появятся
   в списке моделей.
6. Ключ (если нужен платный доступ без аккаунта) — шестерёнка → **Провайдеры** →
   «Ключ OpenCode Zen».

## Документация

- [`AGENTS.md`](AGENTS.md) — архитектура, API, сборка, отладка, грабли.
- [`СДЕЛАНО.md`](СДЕЛАНО.md) — что сделано, что проверено, ограничения.

## Сборка

```powershell
python build_apk.py
adb install --no-incremental -r -d OpenCodeAndroid.apk
python tools\clean_project.py --yes   # после проверки: убрать build/ и старые копии
```

Требуются Android SDK (build-tools + platform), JDK из Android Studio.
Подробности — в `AGENTS.md`.
