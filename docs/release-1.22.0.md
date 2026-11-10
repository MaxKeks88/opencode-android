**Languages / Языки:** English 🇬🇧 · Русский 🇷🇺 · 中文（简体） 🇨🇳 · 中文（繁體） 🇹🇼 · Español 🇪🇸 · Português (Brasil) 🇧🇷 · Deutsch 🇩🇪 · Français 🇫🇷

# OpenCode Android 1.22.0

## New

- **Français** interface language: the whole page (564 dictionary strings + 83 substitutions), native screens and dialogs, the first-run language picker with the 🇫🇷 flag, and `fr-FR` dates. French units (`Mo`, `Ko`, `Go`) are used throughout.
- All eight interface languages are now available: English, Русский, 中文（简体）, 中文（繁體）, Español, Português (Brasil), Deutsch, Français.
- Screenshots of the language picker in every language are in `docs/screenshots/`.

## Under the hood

- Removed the dead PC-panel code (`/pane` viewer, PIN overlay, keyboard and zoom handlers) and its unused translation strings.
- Local site route (`/site`) re-verified end to end: index, CSS, JS, images and nested pages load with correct MIME types; path traversal and unknown capability IDs return 404.

## Notes

- Verified on a phone: every settings tab, provider catalog, web search, pricing, French dates; only user data (chat titles, paths) stays in its original language.
- Updates keep chats, settings and credentials. The APK is checked by SHA-256 and by the signing certificate before installation.

---

Добавлен восьмой язык интерфейса — **французский**: страница, нативные экраны и диалоги, выбор языка при первом запуске с флагом 🇫🇷, даты `fr-FR`, единицы `Mo/Ko/Go`. Теперь доступны все 8 языков. Удалён мёртвый код панели ПК, маршрут `/site` повторно проверен (css/js/картинки/вложенные страницы, обход `..` → 404). Обновление сохраняет чаты, настройки и ключи.
