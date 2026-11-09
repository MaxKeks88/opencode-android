#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""fetch_runtime.py — восстановление бинарного рантайма OpenCode Android из upstream.

Скрипт скачивает и раскладывает ровно те файлы, которые ожидают
``build_apk.py`` и ``Runtime.java``:

  <dest>/runtime/opencode.gz        OpenCode v2.2.6 (arm64 musl), gzip
  <dest>/runtime/rg.gz              ripgrep из Alpine v3.24 (aarch64), gzip
  <dest>/runtime/git/               git 2.54 + ssh/sftp/scp/nc + libs + CA + loader
  <dest>/runtime/python/            CPython 3.12 (python-build-standalone 20261026)
  <dest>/assets/runtime/            musl loader/libc, libstdc++, libgcc_s, pcre2, zlib,
                                    libbz2, liblzma (+ существующий occtl.py не трогаем)

Источники (пины):

  OpenCode  https://github.com/anomalyco/opencode/releases/download/v2.2.6/
            opencode-linux-arm64-musl.tar.gz
  Python    https://github.com/astral-sh/python-build-standalone/releases/download/
            20261026/cpython-3.12.16+20261026-aarch64-unknown-linux-musl-
            install_only_stripped.tar.gz
  Alpine    https://dl-cdn.alpinelinux.org/alpine/v3.24/{main,community}/aarch64/
            (APKINDEX.tar.gz + .apk; версии пакетов берутся из индекса)
  musl loader Alpine v3.24 main `musl` (1.2.6-r3)

Зависимости Alpine резолвятся по APKINDEX (поля P/V/D/p, включая `so:...`),
поэтому набор библиотек в runtime/git/lib определяется автоматически.

SSH-loader: копия musl-loader, в которой строка `/etc/passwd` заменена на
`/sdcard/p` с NUL-добивкой до той же длины (иначе OpenSSH на Android не
находит пользователя в /etc/passwd). Такая же копия кладётся как
ssh-lib/libc.musl-aarch64.so.1.

Примеры:

  # тест в отдельный каталог (реальный рантайм не трогаем)
  python tools/fetch_runtime.py --dest %TEMP%\\runtime-fetch-test --force

  # пересобрать рантайм проекта (явное разрешение на перезапись)
  python tools/fetch_runtime.py --force

Требуется Python 3.8+, только стандартная библиотека (Windows/Linux/macOS).
Скачанные архивы кэшируются в <temp>/oc-runtime-cache (см. --cache/--no-cache).
"""
import argparse
import gzip
import hashlib
import io
import os
import posixpath
import shutil
import struct
import sys
import tarfile
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent

OPENCODE_RELEASE = "v2.2.6"
OPENCODE_ASSET = "opencode-linux-arm64-musl.tar.gz"
OPENCODE_URL = ("https://github.com/anomalyco/opencode/releases/download/%s/%s"
                % (OPENCODE_RELEASE, OPENCODE_ASSET))

PYTHON_RELEASE = "20261026"
PYTHON_ASSET = ("cpython-3.12.16+20261026-aarch64-unknown-linux-musl-"
                "install_only_stripped.tar.gz")
PYTHON_URL = ("https://github.com/astral-sh/python-build-standalone/releases/"
              "download/%s/%s" % (PYTHON_RELEASE, PYTHON_ASSET))

ALPINE_BRANCH = "v3.24"
ALPINE_ARCH = "aarch64"
ALPINE_MIRROR = "https://dl-cdn.alpinelinux.org/alpine"
ALPINE_REPOS = ("main", "community")          # порядок = приоритет при дублях

# Корневые пакеты. Зависимости (libcurl, openssl, pcre2, brotli, ... ) тянутся
# автоматически через APKINDEX, поэтому список короткий.
ALPINE_ROOTS = (
    "git",
    "openssh-client-default",   # тянет openssh-client-common + openssh-keygen
    "netcat-openbsd",
    "ripgrep",
    "musl",
    "libstdc++",
    "libgcc",
    "ca-certificates-bundle",
    "libbz2",                   # для assets/runtime/libbz2.so.1.0
    "xz-libs",                  # для assets/runtime/liblzma.so.5
)

# Эти soname не кладём в runtime/git/lib: libc/loader лежит в ssh-lib,
# libgcc_s — в assets/runtime (App.lib), оба видны через --library-path.
GIT_LIB_EXCLUDE = frozenset(("libc.musl-aarch64.so.1", "libgcc_s.so.1"))

# dlopen-модули OpenSSL (engines/providers) — не видны как DT_NEEDED,
# но нужны для legacy/afalg и т.п. Кладём плоско в runtime/git/lib.
GIT_ENGINE_DIRS = ("usr/lib/engines-3", "usr/lib/ossl-modules")

# Бинарники, которые Runtime.java ждёт в runtime/git/bin.
GIT_BINARIES = ("git", "ssh", "ssh-keygen", "ssh-keyscan", "scp", "sftp", "nc")

# assets/runtime: (имя в dest, кандидаты имени в Alpine-пакетах)
ASSET_LIBS = (
    ("libstdc++.so.6", ("libstdc++.so.6",)),
    ("libgcc_s.so.1", ("libgcc_s.so.1",)),
    ("libpcre2-8.so.0", ("libpcre2-8.so.0",)),
    ("libbz2.so.1.0", ("libbz2.so.1.0", "libbz2.so.1")),
    ("libz.so.1", ("libz.so.1",)),
    ("liblzma.so.5", ("liblzma.so.5",)),
)

# Это должно быть в builtins.txt как минимум (текущий runtime/git/builtins.txt).
# Имена генерируются из usr/libexec/git-core пакета git и объединяются с этим
# списком, чтобы обёртки Runtime.writeGitWrappers() не пропали при смене git.
REQUIRED_BUILTINS = """
add am annotate apply archive backfill bisect blame branch bugreport bundle
cat-file check-attr check-ignore check-mailmap check-ref-format checkout
checkout--worker checkout-index cherry cherry-pick clean clone column commit
commit-graph commit-tree config count-objects credential credential-cache
credential-cache--daemon credential-store describe diagnose diff diff-files
diff-index diff-pairs diff-tree difftool fast-export fetch fetch-pack
fmt-merge-msg for-each-ref for-each-repo format-patch fsck fsck-objects
fsmonitor--daemon gc get-tar-commit-id git grep hash-object help history hook
index-pack init init-db interpret-trailers last-modified log ls-files
ls-remote ls-tree mailinfo mailsplit maintenance merge merge-base merge-file
merge-index merge-ours merge-recursive merge-subtree merge-tree mktag mktree
multi-pack-index mv name-rev notes pack-objects pack-redundant pack-refs
patch-id prune prune-packed pull push range-diff read-tree rebase receive-pack
reflog refs remote remote-ext remote-fd repack replace replay repo rerere reset
restore rev-list rev-parse revert rm send-pack shortlog show show-branch
show-index show-ref sparse-checkout stage stash status stripspace
submodule--helper switch symbolic-ref tag unpack-file unpack-objects
update-index update-ref update-server-info upload-archive upload-pack var
verify-commit verify-pack verify-tag version whatchanged worktree write-tree
""".split()

SSH_LOADER_FROM = b"/etc/passwd"
SSH_LOADER_TO = b"/sdcard/p"

# Обёртки python3/pip в Runtime.java запускают python через
# runtime/python/lib/ld-musl-aarch64.so.1. В архиве python-build-standalone
# загрузчика нет, поэтому кладём копию musl-loader'а. SONAME правим теми же
# двумя байтами, что и в текущем (проверенном на телефоне) дереве:
#   "libc.musl-aarch64.so.1" -> "libc.sosl-aarch64.so.1"
PYTHON_LOADER_FROM = b"libc.musl-aarch64.so.1"
PYTHON_LOADER_TO = b"libc.sosl-aarch64.so.1"

CHUNK = 1 << 20
USER_AGENT = "fetch-runtime/1.0 (OpenCodeAndroid)"
INDEX_TTL = 6 * 3600


# --------------------------------------------------------------------------- utils
def log(msg=""):
    print(msg, flush=True)


def human(n):
    return "%.1f MB" % (n / 1048576.0) if n >= 1048576 else "%.1f KB" % (n / 1024.0)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(CHUNK), b""):
            h.update(chunk)
    return h.hexdigest()


def force_rmtree(path):
    """Удалить каталог, сняв read-only (Google Drive/Windows)."""
    path = Path(path)
    if not path.exists():
        return
    for root, dirs, files in os.walk(str(path), topdown=False):
        for name in files:
            try:
                os.chmod(os.path.join(root, name), 0o777)
            except OSError:
                pass
        for name in dirs:
            try:
                os.chmod(os.path.join(root, name), 0o777)
            except OSError:
                pass
    try:
        os.chmod(str(path), 0o777)
    except OSError:
        pass
    shutil.rmtree(str(path), ignore_errors=True)
    if path.exists():
        shutil.rmtree(str(path), ignore_errors=True)


def download(url, dest, label=None):
    """Скачать url в dest с докачкой-с-нуля и 3 попытками."""
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_name(dest.name + ".part")
    label = label or dest.name
    req = urllib.request.Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept-Encoding": "identity",     # не хотим двойной gzip от CDN
    })
    last_error = None
    for attempt in range(1, 4):
        if part.exists():
            part.unlink()
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                total = int(resp.headers.get("Content-Length") or 0)
                done = 0
                last_print = 0.0
                with open(str(part), "wb") as out:
                    while True:
                        chunk = resp.read(CHUNK)
                        if not chunk:
                            break
                        out.write(chunk)
                        done += len(chunk)
                        now = time.time()
                        if now - last_print > 0.4:
                            last_print = now
                            if total:
                                sys.stdout.write("\r    %s  %5.1f%%  %s/%s   " % (
                                    label, 100.0 * done / total,
                                    human(done), human(total)))
                            else:
                                sys.stdout.write("\r    %s  %s   " % (label, human(done)))
                            sys.stdout.flush()
            if total and part.stat().st_size != total:
                raise IOError("неполная загрузка: %d из %d байт" % (part.stat().st_size, total))
            sys.stdout.write("\r    %s  %s%s\n" % (label, human(part.stat().st_size), " " * 24))
            os.replace(str(part), str(dest))
            return dest
        except (urllib.error.URLError, OSError, IOError) as exc:
            last_error = exc
            sys.stdout.write("\n")
            log("    попытка %d/3 не удалась: %s" % (attempt, exc))
            if attempt < 3:
                time.sleep(2 * attempt)
    raise SystemExit("не удалось скачать %s: %s" % (url, last_error))


# --------------------------------------------------------------------------- cache
class Cache(object):
    def __init__(self, root, enabled=True):
        self.root = Path(root)
        self.enabled = enabled
        self.root.mkdir(parents=True, exist_ok=True)

    def get(self, url, filename, ttl=None):
        path = self.root / filename
        if self.enabled and path.is_file():
            age = time.time() - path.stat().st_mtime
            if ttl is None or age < ttl:
                log("  кэш: %s (%s)" % (filename, human(path.stat().st_size)))
                return path
        log("  загрузка: %s" % url)
        return download(url, path, filename)


# --------------------------------------------------------------------------- APKINDEX
def dep_key(token):
    """`so:libz.so.1=1.4` -> `so:libz.so.1`, `libcrypto3>=3.1.0` -> `libcrypto3`."""
    token = (token or "").strip()
    if not token or token.startswith("!"):
        return None
    cut = [token.find(ch) for ch in "=<>~"]
    cut = [pos for pos in cut if pos > 0]
    if cut:
        token = token[:min(cut)]
    return token or None


def parse_apkindex(raw):
    tf = tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz")
    try:
        member = None
        for item in tf.getmembers():
            if posixpath.basename(item.name) == "APKINDEX":
                member = item
                break
        if member is None:
            raise SystemExit("в APKINDEX.tar.gz нет файла APKINDEX")
        text = tf.extractfile(member).read().decode("utf-8", "replace")
    finally:
        tf.close()

    records = []
    cur = {}
    for line in text.splitlines():
        if not line.strip():
            if cur:
                records.append(cur)
                cur = {}
            continue
        key, sep, value = line.partition(":")
        if not sep:
            continue
        value = value.strip()
        if key in cur:                      # продолжение multi-line поля
            cur[key] = (cur[key] + " " + value).strip()
        else:
            cur[key] = value
    if cur:
        records.append(cur)
    return records


class AlpineIndex(object):
    def __init__(self):
        self.records = []
        self.by_name = {}
        self.by_provide = {}

    def add(self, rec):
        name = rec.get("P")
        if not name:
            return
        self.records.append(rec)
        self.by_name.setdefault(name, rec)              # первый (main) важнее
        for token in rec.get("p", "").split():
            key = dep_key(token)
            if key:
                self.by_provide.setdefault(key, rec)

    def find(self, key):
        return self.by_name.get(key) or self.by_provide.get(key)


def load_alpine_index(cache):
    index = AlpineIndex()
    for repo in ALPINE_REPOS:
        url = "%s/%s/%s/%s/APKINDEX.tar.gz" % (
            ALPINE_MIRROR, ALPINE_BRANCH, repo, ALPINE_ARCH)
        path = cache.get(url, "APKINDEX-%s-%s-%s.tar.gz" % (
            ALPINE_BRANCH, repo, ALPINE_ARCH), ttl=INDEX_TTL)
        with open(str(path), "rb") as fh:
            raw = fh.read()
        for rec in parse_apkindex(raw):
            rec["_repo"] = repo
            index.add(rec)
    log("  APKINDEX: %d пакетов (main+community)" % len(index.records))
    return index


def resolve_packages(index, roots):
    """Вернуть (выбранные пакеты по имени, порядок установки, набор so: soname)."""
    chosen = {}
    order = []
    sonames = set()
    missing = set()
    queue = list(roots)
    while queue:
        key = queue.pop(0)
        if key in chosen or key in missing:
            continue
        rec = index.find(key)
        if rec is None:
            missing.add(key)
            log("  ! в APKINDEX нет зависимости: %s" % key)
            continue
        name = rec["P"]
        if name in chosen:
            continue
        chosen[name] = rec
        order.append(name)
        for token in rec.get("D", "").split():
            dep = dep_key(token)
            if not dep:
                continue
            if dep.startswith("so:"):
                sonames.add(dep[3:])
            queue.append(dep)
    if missing:
        log("  ! неразрешённые зависимости (%d): %s" % (len(missing), ", ".join(sorted(missing))))
    return chosen, order, sonames


# --------------------------------------------------------------------------- staging
def clean_member_name(name):
    while name.startswith("./"):
        name = name[2:]
    if name.startswith("/"):
        name = name.lstrip("/")
    parts = [p for p in name.split("/") if p not in ("", ".")]
    if any(p == ".." for p in parts):
        raise SystemExit("небезопасный путь в архиве: %r" % name)
    return "/".join(parts)


class Sysroot(object):
    """Распакованные .apk в одном дереве (как /) + карта симлинков."""

    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self.files = set()
        self.links = {}
        self.by_base = {}

    def extract_apk(self, apk_path):
        tf = tarfile.open(str(apk_path), "r:gz")
        try:
            for member in tf:
                rel = clean_member_name(member.name)
                if not rel or ("/" not in rel and rel.startswith(".")):
                    continue                          # .PKGINFO / .SIGN.*
                if member.isdir():
                    (self.root / rel).mkdir(parents=True, exist_ok=True)
                elif member.issym():
                    self.links[rel] = member.linkname
                elif member.islnk():
                    self.links[rel] = "/" + clean_member_name(member.linkname)
                elif member.isfile():
                    dst = self.root / rel
                    dst.parent.mkdir(parents=True, exist_ok=True)
                    src = tf.extractfile(member)
                    if src is None:
                        continue
                    with src, open(str(dst), "wb") as out:
                        shutil.copyfileobj(src, out, CHUNK)
                    self.files.add(rel)
                    self.by_base.setdefault(posixpath.basename(rel), []).append(rel)
        finally:
            tf.close()

    def real_rel(self, rel):
        seen = set()
        cur = rel
        for _ in range(64):
            if cur in seen:
                raise SystemExit("циклический симлинк: %s" % rel)
            seen.add(cur)
            target = self.links.get(cur)
            if target is None:
                if cur in self.files:
                    return cur
                raise SystemExit("файла нет в staging: %s" % cur)
            if target.startswith("/"):
                cur = target.lstrip("/")
            else:
                cur = posixpath.normpath(
                    posixpath.join(posixpath.dirname(cur), target))
        raise SystemExit("слишком длинная цепочка симлинков: %s" % rel)

    def find(self, basename, prefer=("usr/lib", "lib")):
        for folder in prefer:
            rel = folder + "/" + basename
            if rel in self.files or rel in self.links:
                return rel
        for rel in self.by_base.get(basename, []):
            return rel
        return None


def emit(sysroot, rel, dest, mode=0o644):
    """Скопировать файл из staging (с раскрытием симлинка) в dest."""
    src = sysroot.root / sysroot.real_rel(rel)
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    with open(str(src), "rb") as fin, open(str(dest), "wb") as fout:
        shutil.copyfileobj(fin, fout, CHUNK)
    os.chmod(str(dest), mode)
    return dest


def emit_bytes(data, dest, mode=0o644):
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    with open(str(dest), "wb") as fh:
        fh.write(data)
    os.chmod(str(dest), mode)
    return dest


def gzip_file(src, dest, level=6):
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_name(dest.name + ".tmp")
    with open(str(src), "rb") as fin, open(str(tmp), "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw,
                           compresslevel=level, mtime=0) as gz:
            shutil.copyfileobj(fin, gz, CHUNK)
    os.replace(str(tmp), str(dest))
    os.chmod(str(dest), 0o644)
    return dest


def safe_extract_tar(tf, dest, transform=None):
    """Распаковать tar, материализуя симлинки копиями (Windows-friendly).

    transform(rel) -> rel|None (None = пропустить элемент).
    """
    dest = Path(dest)
    dest.mkdir(parents=True, exist_ok=True)
    links = []
    for member in tf:
        rel = clean_member_name(member.name)
        if not rel:
            continue
        if transform is not None:
            rel = transform(rel)
            if rel is None:
                continue
        if member.isdir():
            (dest / rel).mkdir(parents=True, exist_ok=True)
        elif member.issym():
            links.append((rel, posixpath.dirname(rel), member.linkname))
        elif member.islnk():
            links.append((rel, "", "/" + clean_member_name(member.linkname)))
        elif member.isfile():
            target = dest / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            src = tf.extractfile(member)
            if src is None:
                continue
            with src, open(str(target), "wb") as out:
                shutil.copyfileobj(src, out, CHUNK)

    # Симлинки: разрешаем внутри распакованного дерева и копируем содержимое.
    pending = list(links)
    for _ in range(8):
        rest = []
        for rel, base, target in pending:
            src_rel = target.lstrip("/") if target.startswith("/") else \
                posixpath.normpath(posixpath.join(base, target))
            src = dest / src_rel
            if src.is_file():
                out = dest / rel
                out.parent.mkdir(parents=True, exist_ok=True)
                if os.path.normcase(os.path.abspath(str(src))) == \
                        os.path.normcase(os.path.abspath(str(out))):
                    continue        # Windows: имена различаются только регистром
                try:
                    shutil.copyfile(src, str(out))
                except shutil.SameFileError:
                    continue
            else:
                rest.append((rel, base, target))
        if not rest:
            break
        if len(rest) == len(pending):
            for rel, _base, target in rest:
                log("  ! симлинк не восстановлен: %s -> %s" % (rel, target))
            break
        pending = rest


# --------------------------------------------------------------------------- ELF
def elf_needed(path):
    """Список DT_NEEDED (soname) из ELF. Без внешних утилит, только struct."""
    try:
        with open(str(path), "rb") as fh:
            data = fh.read()
    except OSError:
        return []
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[4] not in (1, 2):
        return []
    is64 = data[4] == 2
    endian = "<" if data[5] == 1 else ">"
    try:
        if is64:
            phoff = struct.unpack_from(endian + "Q", data, 0x20)[0]
            phentsize = struct.unpack_from(endian + "H", data, 0x36)[0]
            phnum = struct.unpack_from(endian + "H", data, 0x38)[0]
        else:
            phoff = struct.unpack_from(endian + "I", data, 0x1C)[0]
            phentsize = struct.unpack_from(endian + "H", data, 0x2A)[0]
            phnum = struct.unpack_from(endian + "H", data, 0x2C)[0]
        loads = []
        dynamic = None
        for i in range(phnum):
            off = phoff + i * phentsize
            if is64:
                p_type, _flags, p_off, p_vaddr, _pa, p_filesz = \
                    struct.unpack_from(endian + "IIQQQQ", data, off)
            else:
                p_type, p_off, p_vaddr, _pa, p_filesz = \
                    struct.unpack_from(endian + "IIIII", data, off)
            if p_type == 1:                       # PT_LOAD
                loads.append((p_vaddr, p_off, p_filesz))
            elif p_type == 2:                     # PT_DYNAMIC
                dynamic = (p_off, p_off + p_filesz)
        if dynamic is None:
            return []

        def vaddr_to_off(addr):
            for vaddr, off, filesz in loads:
                if vaddr <= addr < vaddr + filesz:
                    return off + (addr - vaddr)
            return None

        entsize = 16 if is64 else 8
        needed_offs = []
        strtab = None
        pos, end = dynamic
        while pos + entsize <= end:
            tag, val = struct.unpack_from(
                endian + ("qQ" if is64 else "iI"), data, pos)
            pos += entsize
            if tag == 0:                          # DT_NULL
                break
            if tag == 1:                          # DT_NEEDED
                needed_offs.append(val)
            elif tag == 5:                        # DT_STRTAB
                strtab = val
        if strtab is None:
            return []
        base = vaddr_to_off(strtab)
        if base is None:
            return []

        names = []
        for off in needed_offs:
            start = base + off
            if start >= len(data):
                continue
            stop = data.find(b"\0", start)
            if stop < 0:
                continue
            names.append(data[start:stop].decode("ascii", "replace"))
        return names
    except (struct.error, IndexError):
        return []


def needed_closure(sysroot, roots, exclude):
    """Транзитивное замыкание DT_NEEDED для набора файлов staging."""
    found = set()
    missing = set()
    seen = set()
    queue = list(roots)
    while queue:
        rel = queue.pop(0)
        real = sysroot.real_rel(rel)
        if real in seen:
            continue
        seen.add(real)
        for soname in elf_needed(sysroot.root / real):
            if soname in exclude:
                continue
            lib_rel = sysroot.find(soname, prefer=("usr/lib", "lib"))
            if lib_rel is None:
                missing.add(soname)
                continue
            if soname not in found:
                found.add(soname)
                queue.append(lib_rel)
    return found, missing


# --------------------------------------------------------------------------- alpine outputs
def apk_url(rec):
    return "%s/%s/%s/%s/%s-%s.apk" % (
        ALPINE_MIRROR, ALPINE_BRANCH, rec["_repo"], ALPINE_ARCH,
        rec["P"], rec["V"])


def musl_loader(ctx):
    """Байты musl-loader (lib/ld-musl-aarch64.so.1) из пакета musl."""
    if ctx.loader is not None:
        return ctx.loader
    index = load_alpine_index(ctx.cache)
    rec = index.find("musl")
    if rec is None:
        raise SystemExit("в APKINDEX нет пакета musl")
    apk = ctx.cache.get(apk_url(rec), "musl-%s.apk" % rec["V"])
    data = None
    tf = tarfile.open(str(apk), "r:gz")
    try:
        for member in tf:
            if clean_member_name(member.name) == "lib/ld-musl-aarch64.so.1":
                data = tf.extractfile(member).read()
                break
    finally:
        tf.close()
    if not data:
        raise SystemExit("в пакете musl нет lib/ld-musl-aarch64.so.1")
    ctx.loader = data
    return data


def build_alpine(ctx):
    log("[alpine] APKINDEX %s/%s" % (ALPINE_BRANCH, ALPINE_ARCH))
    index = load_alpine_index(ctx.cache)
    chosen, order, sonames = resolve_packages(index, ALPINE_ROOTS)

    log("[alpine] пакеты (%d):" % len(order))
    for name in order:
        rec = chosen[name]
        log("  %-24s %-18s %s" % (name, rec["V"], rec["_repo"]))

    sysroot = Sysroot(ctx.tmp / "sysroot")
    for name in order:
        rec = chosen[name]
        apk = ctx.cache.get(apk_url(rec), "%s-%s.apk" % (name, rec["V"]))
        sysroot.extract_apk(apk)

    git_dir = ctx.out / "runtime" / "git"
    lib_dir = git_dir / "lib"
    bin_dir = git_dir / "bin"

    missing = []
    bin_rels = []

    # bin/*
    for name in GIT_BINARIES:
        rel = sysroot.find(name, prefer=("usr/bin", "bin"))
        if rel is None:
            missing.append("bin/" + name)
            continue
        emit(sysroot, rel, bin_dir / name, 0o755)
        bin_rels.append(rel)

    # libexec/git-core/git-remote-http.real
    rel = sysroot.find("git-remote-http", prefer=("usr/libexec/git-core",))
    if rel is None:
        missing.append("libexec/git-core/git-remote-http.real")
    else:
        emit(sysroot, rel, git_dir / "libexec" / "git-core" / "git-remote-http.real", 0o755)
        bin_rels.append(rel)

    # share/git-core/templates/*
    prefix = "usr/share/git-core/templates/"
    for rel in sorted(sysroot.files):
        if rel.startswith(prefix):
            emit(sysroot, rel, git_dir / "share" / "git-core" / "templates" /
                 rel[len(prefix):], 0o644)
    templates_dir = git_dir / "share" / "git-core" / "templates"
    templates_dir.mkdir(parents=True, exist_ok=True)
    if not any(templates_dir.iterdir()):
        # в пакете git каталог пустой; .keep нужен, чтобы GIT_TEMPLATE_DIR жил
        emit_bytes(b"", templates_dir / ".keep", 0o644)

    # etc/ssl/certs/ca-certificates.crt
    rel = "etc/ssl/certs/ca-certificates.crt"
    if rel not in sysroot.files:
        missing.append(rel)
    else:
        emit(sysroot, rel, git_dir / "etc" / "ssl" / "certs" /
             "ca-certificates.crt", 0o644)

    # lib/*.so — реальные DT_NEEDED бинарников (ssh/git/nc/git-remote-http
    # и dlopen-модулей OpenSSL). APKINDEX даёт пакеты, ELF — точный набор libs.
    engine_rels = []
    for folder in GIT_ENGINE_DIRS:
        for rel in sorted(sysroot.files):
            if rel.startswith(folder + "/"):
                engine_rels.append(rel)

    libs, missing_libs = needed_closure(sysroot, bin_rels + engine_rels, GIT_LIB_EXCLUDE)
    for soname in sorted(missing_libs):
        missing.append("lib/" + soname)

    # Плюс soname, которые APKINDEX объявил зависимостями пакетов: там бывают
    # библиотеки, нужные не самим бинарникам, а подгружаемым частям
    # (например libexpat для git-remote-http).
    for soname in sorted(sonames):
        if soname in GIT_LIB_EXCLUDE:
            continue
        if sysroot.find(soname, prefer=("usr/lib", "lib")) is None:
            missing.append("lib/" + soname)
            continue
        libs.add(soname)

    for soname in sorted(libs):
        emit(sysroot, sysroot.find(soname, prefer=("usr/lib", "lib")),
             lib_dir / soname, 0o755)
    log("  lib: %d библиотек (DT_NEEDED + APKINDEX)" % len(libs))

    # lib/*.so — OpenSSL engines/modules (dlopen, не DT_NEEDED)
    for rel in engine_rels:
        emit(sysroot, rel, lib_dir / posixpath.basename(rel), 0o755)

    # builtins.txt: из usr/libexec/git-core пакета git + обязательный минимум
    builtins = set()
    for rel, target in list(sysroot.links.items()):
        if posixpath.dirname(rel) != "usr/libexec/git-core":
            continue
        base = posixpath.basename(rel)
        if target.endswith("bin/git"):
            if base == "git":
                builtins.add("git")
            elif base.startswith("git-"):
                builtins.add(base[4:])
    generated = set(builtins)
    builtins |= set(REQUIRED_BUILTINS)
    (git_dir / "builtins.txt").parent.mkdir(parents=True, exist_ok=True)
    with open(str(git_dir / "builtins.txt"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(sorted(builtins)) + "\n")
    log("  builtins.txt: %d имён (из пакета %d, добавлено из минимума %d)"
        % (len(builtins), len(generated), len(builtins - generated)))

    # ripgrep -> runtime/rg.gz
    rel = sysroot.find("rg", prefer=("usr/bin", "bin"))
    if rel is None:
        missing.append("rg")
    else:
        dest = gzip_file(sysroot.root / sysroot.real_rel(rel), ctx.out / "runtime" / "rg.gz")
        log("  rg.gz: %s" % human(dest.stat().st_size))

    # assets/runtime
    assets = ctx.out / "assets" / "runtime"
    assets.mkdir(parents=True, exist_ok=True)

    loader_rel = "lib/ld-musl-aarch64.so.1"
    if loader_rel not in sysroot.files:
        missing.append(loader_rel)
        loader = None
    else:
        loader = (sysroot.root / loader_rel).read_bytes()
        ctx.loader = loader
        emit_bytes(loader, assets / "ld-musl-aarch64.so.1", 0o755)
        emit_bytes(loader, assets / "libc.musl-aarch64.so.1", 0o755)

        # ssh-lib: патченный loader (см. AGENTS.md, п.20)
        patched = patch_ssh_loader(loader)
        ssh_lib = git_dir / "ssh-lib"
        emit_bytes(patched, ssh_lib / "ld-musl-aarch64.so.1", 0o755)
        emit_bytes(patched, ssh_lib / "libc.musl-aarch64.so.1", 0o755)

    for dest_name, candidates in ASSET_LIBS:
        rel = None
        for candidate in candidates:
            rel = sysroot.find(candidate)
            if rel is not None:
                break
        if rel is None:
            log("  ! нет upstream-файла для assets/runtime/%s (пропуск)" % dest_name)
            continue
        emit(sysroot, rel, assets / dest_name, 0o755)

    return missing


def patch_ssh_loader(data):
    """Заменить `/etc/passwd` на `/sdcard/p`, сохранив длину строки (NUL-паддинг)."""
    needle = SSH_LOADER_FROM
    replacement = SSH_LOADER_TO
    pos = data.find(needle)
    if pos < 0:
        raise SystemExit("в musl-loader не найдена строка %r" % needle)
    if data.find(needle, pos + 1) >= 0:
        raise SystemExit("в musl-loader несколько вхождений %r — проверьте патч" % needle)
    out = bytearray(data)
    end = pos + len(needle)
    while end < len(out) and out[end] == 0:
        end += 1
    if end - pos < len(replacement):
        raise SystemExit("слишком короткий слот для %r" % replacement)
    out[pos:pos + len(replacement)] = replacement
    for i in range(pos + len(replacement), end):
        out[i] = 0
    return bytes(out)


# --------------------------------------------------------------------------- opencode
def build_opencode(ctx):
    log("[opencode] %s / %s" % (OPENCODE_RELEASE, OPENCODE_ASSET))
    archive = ctx.cache.get(OPENCODE_URL,
                            "opencode-%s-linux-arm64-musl.tar.gz" % OPENCODE_RELEASE)
    extract_dir = ctx.tmp / "opencode-src"
    tf = tarfile.open(str(archive), "r:gz")
    try:
        binary = None
        for member in tf.getmembers():
            if member.isfile() and posixpath.basename(member.name) == "opencode":
                binary = member
                break
        if binary is None:
            raise SystemExit("в %s нет файла opencode" % archive.name)
        extract_dir.mkdir(parents=True, exist_ok=True)
        src = tf.extractfile(binary)
        target = extract_dir / "opencode"
        with src, open(str(target), "wb") as out:
            shutil.copyfileobj(src, out, CHUNK)
    finally:
        tf.close()

    log("  бинарь opencode: %s  sha256=%s"
        % (human(target.stat().st_size), sha256_file(target)[:16]))
    dest = gzip_file(target, ctx.out / "runtime" / "opencode.gz", level=6)
    log("  opencode.gz: %s" % human(dest.stat().st_size))
    return dest


# --------------------------------------------------------------------------- python
def build_python(ctx):
    log("[python] python-build-standalone %s" % PYTHON_RELEASE)
    archive = ctx.cache.get(PYTHON_URL, PYTHON_ASSET)

    def strip_root(rel):
        if rel.startswith("python/"):
            return rel[len("python/"):]
        return rel

    log("  распаковка: %s" % human(archive.stat().st_size))
    tf = tarfile.open(str(archive), "r:gz")
    try:
        target = ctx.out / "runtime" / "python"
        target.mkdir(parents=True, exist_ok=True)
        safe_extract_tar(tf, target, transform=strip_root)
    finally:
        tf.close()

    # bin/* должны быть исполняемыми (APK не хранит права; Runtime chmod'ит
    # только свои обёртки, поэтому помечаем заранее).
    bin_dir = target / "bin"
    count = 0
    for root, _dirs, files in os.walk(str(bin_dir)):
        for name in files:
            try:
                os.chmod(os.path.join(root, name), 0o755)
            except OSError:
                pass
            count += 1
    log("  python/bin: %d файлов" % count)

    # Загрузчик: в архиве его нет, а обёртки Runtime.java запускают
    # "$P/lib/ld-musl-aarch64.so.1". Берём musl-loader и правим SONAME
    # так же, как в текущем дереве (см. PYTHON_LOADER_*).
    if len(PYTHON_LOADER_FROM) != len(PYTHON_LOADER_TO):
        raise SystemExit("патч SONAME python-загрузчика должен сохранять длину")
    loader = bytearray(musl_loader(ctx))
    pos = loader.find(PYTHON_LOADER_FROM)
    if pos < 0:
        raise SystemExit("в musl-loader нет строки %r" % PYTHON_LOADER_FROM)
    loader[pos:pos + len(PYTHON_LOADER_TO)] = PYTHON_LOADER_TO
    emit_bytes(bytes(loader), target / "lib" / "ld-musl-aarch64.so.1", 0o755)
    log("  python/lib/ld-musl-aarch64.so.1: %s (musl-loader + SONAME-патч)"
        % human(len(loader)))

    # sitecustomize.py — файл проекта (не из архива): правит sys.executable и
    # musllinux-тег pip. Если он есть в текущем дереве назначения, сохраняем.
    old_side = ctx.dest / "runtime" / "python" / "lib" / "python3.12" / "sitecustomize.py"
    if old_side.is_file():
        new_side = target / "lib" / "python3.12" / "sitecustomize.py"
        new_side.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(str(old_side), str(new_side))
        log("  sitecustomize.py: сохранён из текущего дерева")
    return target


# --------------------------------------------------------------------------- main
def protected_targets(dest):
    return [
        dest / "runtime" / "opencode.gz",
        dest / "runtime" / "rg.gz",
        dest / "runtime" / "git",
        dest / "runtime" / "python",
        dest / "assets" / "runtime" / "ld-musl-aarch64.so.1",
        dest / "assets" / "runtime" / "libc.musl-aarch64.so.1",
    ] + [dest / "assets" / "runtime" / name for name, _ in ASSET_LIBS]


def copy_tree(src, dest, force):
    if dest.exists():
        if not force:
            raise SystemExit("каталог уже существует: %s (нужен --force)" % dest)
        force_rmtree(dest)
    shutil.copytree(str(src), str(dest))


def install_outputs(ctx):
    dest = ctx.dest
    force = ctx.force

    existing = [p for p in protected_targets(dest) if p.exists()]
    if existing and not force:
        log("")
        log("Каталог назначения уже содержит рантайм:")
        for path in existing:
            log("  %s" % path)
        raise SystemExit(
            "Перезапись запрещена. Укажите --force (или другой --dest).")

    # runtime/*.gz — файлами
    for name in ("opencode.gz", "rg.gz"):
        src = ctx.out / "runtime" / name
        if not src.is_file():
            continue
        target = dest / "runtime" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists() and not force:
            raise SystemExit("файл существует: %s" % target)
        shutil.copyfile(str(src), str(target))
        os.chmod(str(target), 0o644)
        log("  + runtime/%s (%s)" % (name, human(target.stat().st_size)))

    # runtime/git, runtime/python — деревьями
    for name in ("git", "python"):
        src = ctx.out / "runtime" / name
        if not src.is_dir():
            continue
        target = dest / "runtime" / name
        copy_tree(src, target, force)
        files = sum(len(files) for _r, _d, files in os.walk(str(target)))
        size = sum(os.path.getsize(os.path.join(r, f))
                   for r, _d, fs in os.walk(str(target)) for f in fs)
        log("  + runtime/%s/ (%d файлов, %s)" % (name, files, human(size)))

    # assets/runtime — только перечисленные файлы (occtl.py и manifest.txt не трогаем)
    src_assets = ctx.out / "assets" / "runtime"
    dst_assets = dest / "assets" / "runtime"
    if src_assets.is_dir():
        dst_assets.mkdir(parents=True, exist_ok=True)
        for src in sorted(src_assets.iterdir()):
            if not src.is_file():
                continue
            target = dst_assets / src.name
            if target.exists() and not force:
                raise SystemExit("файл существует: %s" % target)
            shutil.copyfile(str(src), str(target))
            os.chmod(str(target), 0o755)
            log("  + assets/runtime/%s (%s)" % (src.name, human(target.stat().st_size)))
    if (dst_assets / "occtl.py").exists():
        log("  = assets/runtime/occtl.py не изменён")


def parse_args(argv):
    parser = argparse.ArgumentParser(
        description="Собрать runtime/ и assets/runtime/ из upstream (OpenCode, Alpine, CPython).")
    parser.add_argument("--dest", default=str(PROJECT_ROOT),
                        help="каталог проекта для записи (по умолчанию корень проекта)")
    parser.add_argument("--force", action="store_true",
                        help="перезаписать существующий runtime/ и assets/runtime/")
    parser.add_argument("--cache", default=str(Path(tempfile.gettempdir()) / "oc-runtime-cache"),
                        help="каталог кэша архивов (по умолчанию <temp>/oc-runtime-cache)")
    parser.add_argument("--no-cache", action="store_true",
                        help="не использовать кэш (качать заново во временный каталог)")
    parser.add_argument("--only", nargs="*", choices=["opencode", "alpine", "python"],
                        metavar="SECTION",
                        help="собрать только указанные части: opencode alpine python")
    return parser.parse_args(argv)


class Context(object):
    pass


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)

    dest = Path(args.dest).expanduser().resolve()
    dest.mkdir(parents=True, exist_ok=True)
    sections = set(args.only or ("opencode", "alpine", "python"))

    temp_root = Path(tempfile.mkdtemp(prefix="oc-runtime-fetch-"))
    cache = Cache(temp_root / "cache", enabled=False) if args.no_cache \
        else Cache(args.cache)

    ctx = Context()
    ctx.dest = dest
    ctx.force = args.force
    ctx.tmp = temp_root
    ctx.out = temp_root / "out"
    ctx.cache = cache
    ctx.loader = None
    ctx.out.mkdir(parents=True, exist_ok=True)

    log("OpenCode Android: сборка рантайма из upstream")
    log("  назначение : %s" % dest)
    log("  кэш        : %s%s" % (cache.root, " (без кэша)" if not cache.enabled else ""))
    log("  разделы    : %s" % ", ".join(sorted(sections)))
    log("")

    missing = []
    started = time.time()
    try:
        if "alpine" in sections:
            missing += build_alpine(ctx)
        if "opencode" in sections:
            build_opencode(ctx)
        if "python" in sections:
            build_python(ctx)

        log("")
        log("[запись] %s" % dest)
        install_outputs(ctx)
    finally:
        shutil.rmtree(str(temp_root), ignore_errors=True)

    log("")
    if missing:
        log("НЕ НАЙДЕНО (%d):" % len(missing))
        for item in missing:
            log("  - %s" % item)
    else:
        log("Все ожидаемые файлы собраны.")
    log("Готово за %.1f c." % (time.time() - started))
    if dest == PROJECT_ROOT and not args.force:
        log("(это корень проекта; для реальной перезаписи нужен --force)")
    return 0 if not missing else 1


if __name__ == "__main__":
    sys.exit(main())
