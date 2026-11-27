**Languages / Языки:** English 🇬🇧 · Русский 🇷🇺 · 中文（简体） 🇨🇳 · 中文（繁體） 🇹🇼 · Español 🇪🇸 · Português (Brasil) 🇧🇷 · Deutsch 🇩🇪 · Français 🇫🇷

# OpenCode Android 1.25.3

## New

- The **＋** button now opens the phone's file explorer directly, at the root of internal storage (`/storage/emulated/0`). The intermediate "File from phone / File / Folder / Where to work" sheet is gone — one tap and you are picking a file.
- The shortcut chips above the list are now **Download**, **Pictures** and **Screenshots**. The screenshots folder is detected on the device (`Pictures/Screenshots`, `DCIM/Screenshots`, `Screenshots`, or any `*screenshot*` folder); if the phone has none, the chip is simply not shown.
- Attaching a folder did not disappear: inside any folder the explorer shows a **📁 Attach this folder** row (it is hidden at the storage root).

## Notes

- The working folder is still chosen from the side menu ("Where to work").
- Verified on a phone: the button lands on internal storage, all three chips navigate to real folders, a file from `Download` gets attached to the message and removed again, and the folder row appears only inside folders.
- Updates keep chats, settings and credentials. The APK is checked by SHA-256 and by the signing certificate before installation.

---

# OpenCode Android 1.25.3

## Новое

- Кнопка **＋** теперь сразу открывает проводник телефона в корне внутренней памяти (`/storage/emulated/0`). Промежуточный лист «Файл с телефона / Файл / Папка / Где работать» убран — одно нажатие, и вы выбираете файл.
- Ссылки над списком теперь **Download**, **Pictures** и **Screenshots**. Папка скриншотов определяется на самом устройстве (`Pictures/Screenshots`, `DCIM/Screenshots`, `Screenshots` или любая папка вида `*screenshot*`); если её нет — чипа просто не будет.
- Вложение папки никуда не делось: внутри любого каталога есть строка **📁 Прикрепить эту папку** (в корне памяти она скрыта).

## Заметки

- Рабочая папка по-прежнему выбирается в боковом меню («Где работать»).
- Проверено на телефоне: кнопка открывает корень памяти, все три ссылки ведут в реальные папки, файл из `Download` прикрепляется к сообщению и убирается крестиком, строка вложения папки показывается только внутри папок.
- Обновление сохраняет чаты, настройки и ключи. APK сверяется по SHA-256 и по сертификату подписи перед установкой.
