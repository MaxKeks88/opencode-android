#!/usr/bin/env python3
"""Уборка проекта после рабочей сборки APK.

Оставляет только рабочие файлы и один OpenCodeAndroid.apk, удаляя:
  * build/ и конфликтные копии build (1), build (2), ...
  * *.idsig (подписи старых APK)
  * конфликтные копии файлов вида "имя (1).ext" в корне проекта
  * старые копии APK "OpenCodeAndroid (1).apk" и т.п.

По умолчанию — только показывает, что будет удалено (dry-run).
Удаление: python tools/clean_project.py --yes
"""
import os
import re
import shutil
import stat
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent

# что никогда не трогаем
KEEP_DIRS = {"app", "assets", "runtime", "tools"}
KEEP_FILES = {"OpenCodeAndroid.apk"}


def size_of(path: Path) -> int:
    if path.is_file():
        try:
            return path.stat().st_size
        except OSError:
            return 0
    total = 0
    for root, dirs, files in os.walk(path):
        for name in files:
            try:
                total += os.path.getsize(os.path.join(root, name))
            except OSError:
                pass
    return total


def force_rm(path: Path):
    """Drive/Windows могут ставить read-only — снимаем и удаляем."""
    if not path.exists():
        return
    if path.is_dir():
        for root, dirs, files in os.walk(path, topdown=False):
            for name in files:
                try:
                    os.chmod(os.path.join(root, name), stat.S_IWRITE)
                except OSError:
                    pass
            for name in dirs:
                try:
                    os.chmod(os.path.join(root, name), stat.S_IWRITE)
                except OSError:
                    pass
        try:
            os.chmod(path, stat.S_IWRITE)
        except OSError:
            pass
        shutil.rmtree(path, ignore_errors=True)
    else:
        try:
            os.chmod(path, stat.S_IWRITE)
        except OSError:
            pass
        try:
            path.unlink()
        except OSError:
            pass


def targets():
    """Возвращает список (path, reason) для удаления."""
    out = []
    if not BASE.exists():
        return out

    build_re = re.compile(r"^build( \(\d+\))?$")
    conflict_re = re.compile(r" \(\d+\)\.")

    for entry in sorted(BASE.iterdir(), key=lambda p: p.name.lower()):
        name = entry.name
        if entry.is_dir():
            if name in KEEP_DIRS:
                continue
            if build_re.match(name):
                out.append((entry, "промежуточная сборка / конфликтная копия"))
            elif name == "__pycache__":
                out.append((entry, "кэш Python"))
            continue

        # файлы в корне
        if name in KEEP_FILES or name == "OpenCodeAndroid.apk":
            continue
        if name.lower().endswith(".idsig"):
            out.append((entry, "подпись старого APK"))
        elif conflict_re.search(name):
            out.append((entry, "конфликтная копия (Drive)"))
        elif re.match(r"^OpenCodeAndroid.*\.apk$", name, re.I):
            out.append((entry, "старая копия APK"))

    tools_cache = BASE / "tools" / "__pycache__"
    if tools_cache.is_dir():
        out.append((tools_cache, "кэш Python"))

    return out


def main() -> int:
    do_it = "--yes" in sys.argv or "-y" in sys.argv
    items = targets()

    if not items:
        print("Нечего удалять: папка уже чистая.")
        return 0

    total = 0
    print("Будет удалено:" if not do_it else "Удаляю:")
    for path, reason in items:
        sz = size_of(path)
        total += sz
        kind = "DIR " if path.is_dir() else "FILE"
        print("  %-4s %10.2f MB  %-38s  %s" % (kind, sz / 1048576, path.name, reason))

    print("\nИтого: %.2f MB" % (total / 1048576))
    if not do_it:
        print("\nЭто предварительный просмотр. Для удаления: python tools/clean_project.py --yes")
        return 0

    for path, _ in items:
        force_rm(path)
    print("Готово. Осталось рабочих файлов: OpenCodeAndroid.apk + исходники/рантайм.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
