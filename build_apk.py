#!/usr/bin/env python3
"""Сборка APK без Gradle: aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner."""
import os, re, shutil, subprocess, sys, zipfile
from pathlib import Path

BASE = Path(os.path.abspath(__file__)).parent
APP = BASE / "app"
BUILD = BASE / "build"
ASSETS = BASE / "assets"
LIBS = BASE / "libs"
OUT = BASE / "OpenCodeAndroid.apk"

SDK = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or
           (Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk"))
JAVA_HOME = Path(os.environ.get("JAVA_HOME") or r"C:\Program Files\Android\Android Studio\jbr")
os.environ["JAVA_HOME"] = str(JAVA_HOME)   # d8.bat / apksigner.bat берут java из JAVA_HOME
JAVA = JAVA_HOME / "bin" / ("java.exe" if os.name == "nt" else "java")
JAVAC = JAVA_HOME / "bin" / ("javac.exe" if os.name == "nt" else "javac")
JAR = JAVA_HOME / "bin" / ("jar.exe" if os.name == "nt" else "jar")
KEYTOOL = JAVA_HOME / "bin" / ("keytool.exe" if os.name == "nt" else "keytool")

def run(cmd, **kw):
    print("+", " ".join(str(c) for c in cmd))
    r = subprocess.run([str(c) for c in cmd], **kw)
    if r.returncode != 0:
        raise SystemExit("command failed: %s" % (cmd[0],))
    return r

def find_tools():
    bt = sorted((SDK / "build-tools").glob("*"), key=lambda p: [int(x) for x in re.findall(r"\d+", p.name)])
    if not bt:
        raise SystemExit("build-tools not found in %s" % SDK)
    btd = bt[-1]
    aapt2 = btd / ("aapt2.exe" if os.name == "nt" else "aapt2")
    zipalign = btd / ("zipalign.exe" if os.name == "nt" else "zipalign")
    apksigner = btd / ("apksigner.bat" if os.name == "nt" else "apksigner")
    d8 = btd / ("d8.bat" if os.name == "nt" else "d8")
    platforms = sorted((SDK / "platforms").glob("android-*"),
                       key=lambda p: [int(x) for x in re.findall(r"\d+", p.name)])
    if not platforms:
        raise SystemExit("android platform not found")
    android_jar = platforms[-1] / "android.jar"
    return aapt2, zipalign, apksigner, d8, android_jar

DEV_PASSWORDS = {"opencode2026"}

def keystore(release):
    ks = BASE / "keystore.jks"
    props = BASE / "keystore.properties"
    if not props.exists():
        if release:
            raise SystemExit(
                "\n[релиз] нет keystore.properties.\n"
                "Создайте файл с своими паролями:\n"
                "  storePassword=<пароль>\n  keyPassword=<пароль>\n  alias=opencode\n")
        props.write_text("storePassword=opencode2026\nkeyPassword=opencode2026\nalias=opencode\n",
                         encoding="utf-8")
        print("ВНИМАНИЕ: создан keystore.properties с паролями по умолчанию "
              "(только для отладочной сборки)")
    cfg = {}
    for line in props.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            cfg[k.strip()] = v.strip()
    if release and (cfg.get("storePassword") in DEV_PASSWORDS
                    or cfg.get("keyPassword") in DEV_PASSWORDS):
        raise SystemExit(
            "\n[релиз] в keystore.properties пароли по умолчанию (opencode2026).\n"
            "Для релизной сборки задайте свои пароли — иначе APK нельзя публиковать.")
    if not ks.exists():
        if release:
            raise SystemExit(
                "\n[релиз] нет keystore.jks. Создайте ключ:\n"
                "  keytool -genkeypair -keystore keystore.jks -alias opencode "
                "-keyalg RSA -keysize 2048 -validity 10000\n")
        print("ВНИМАНИЕ: создаётся отладочный keystore.jks с паролями по умолчанию")
        run([KEYTOOL, "-genkeypair", "-keystore", ks, "-alias", cfg.get("alias", "opencode"),
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
             "-storepass", cfg.get("storePassword", "opencode2026"),
             "-keypass", cfg.get("keyPassword", "opencode2026"),
             "-dname", "CN=OpenCode Android, O=OpenCode, C=RU"])
    dev = (cfg.get("storePassword") in DEV_PASSWORDS or cfg.get("keyPassword") in DEV_PASSWORDS)
    print("подпись:", ks, "(ОТЛАДОЧНЫЙ ключ с паролями по умолчанию)" if dev else "(свой ключ)")
    return ks, cfg

def force_rmtree(p):
    """Windows/Google Drive могут ставить read-only на каталоги — снимаем и удаляем."""
    if not p.exists():
        return
    for root, dirs, files in os.walk(p, topdown=False):
        for name in files:
            try: os.chmod(os.path.join(root, name), 0o777)
            except Exception: pass
        for name in dirs:
            try: os.chmod(os.path.join(root, name), 0o777)
            except Exception: pass
    try: os.chmod(p, 0o777)
    except Exception: pass
    shutil.rmtree(p, ignore_errors=True)
    if p.exists():
        shutil.rmtree(p, ignore_errors=True)

def runtime_manifest():
    """SHA-256 всех runtime-файлов → assets/runtime/manifest.txt.

    Runtime.ensure() использует хеш этого файла как маркер: меняются
    бинарники — меняется маркер; меняется только версия приложения —
    маркер тот же, и 250 МБ заново не распаковываются.
    """
    import hashlib
    items = []
    for root in (ASSETS / "runtime", BASE / "runtime"):
        if not root.exists():
            continue
        for p in sorted(root.rglob("*")):
            if not p.is_file():
                continue
            rel = p.relative_to(root).as_posix()
            if rel == "manifest.txt":
                continue
            h = hashlib.sha256()
            with open(p, "rb") as fh:
                for chunk in iter(lambda: fh.read(1024 * 1024), b""):
                    h.update(chunk)
            items.append((rel, h.hexdigest(), p.stat().st_size))
    items.sort()
    lines = ["%s  %d  %s" % (digest, size, name) for name, digest, size in items]
    target = ASSETS / "runtime" / "manifest.txt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("манифест рантайма: %d файлов, %s" % (len(items), target))


def main():
    release = "--release" in sys.argv[1:]
    if release:
        print("режим: РЕЛИЗ (строгая проверка ключа подписи)")
    aapt2, zipalign, apksigner, d8, android_jar = find_tools()
    print("SDK:", SDK)
    print("android.jar:", android_jar)

    # проверяем подпись сразу: при --release ошибки должны всплыть до сборки
    ks, cfg = keystore(release)

    force_rmtree(BUILD)
    (BUILD / "gen").mkdir(parents=True)
    (BUILD / "classes").mkdir()
    (BUILD / "dex").mkdir()

    runtime_manifest()

    # 1. ресурсы
    run([aapt2, "compile", "--dir", APP / "res", "-o", BUILD / "res.zip"])

    # 2. link (маленькие assets; тяжёлый runtime добавим позже)
    run([aapt2, "link", "-o", BUILD / "base.apk", "-I", android_jar,
         "--manifest", APP / "AndroidManifest.xml", "-R", BUILD / "res.zip",
         "-A", ASSETS, "--java", BUILD / "gen", "--auto-add-overlay"])

    # 3. java
    libs = sorted(LIBS.glob("*.jar")) if LIBS.exists() else []
    classpath = [str(android_jar)] + [str(p) for p in libs]
    srcs = [str(p) for p in (APP / "src").rglob("*.java")]
    srcs += [str(p) for p in (BUILD / "gen").rglob("*.java")]
    run([JAVAC, "-encoding", "UTF-8", "-source", "11", "-target", "11",
         "-classpath", os.pathsep.join(classpath), "-d", BUILD / "classes"] + srcs)

    # 4. dex
    run([JAR, "cf", BUILD / "classes.jar", "-C", BUILD / "classes", "."])
    run([d8, "--lib", android_jar, "--min-api", "26", "--output", BUILD / "dex",
         BUILD / "classes.jar"] + [str(p) for p in libs])

    # 5. собрать APK
    out = BUILD / "unsigned.apk"
    with zipfile.ZipFile(BUILD / "base.apk", "r") as zin, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            zout.writestr(item, zin.read(item.filename))
        zout.write(BUILD / "dex" / "classes.dex", "classes.dex", compress_type=zipfile.ZIP_DEFLATED)
        big = BASE / "runtime"
        if big.exists():
            for p in sorted(big.rglob("*")):
                if not p.is_file():
                    continue
                rel = "assets/runtime/" + p.relative_to(big).as_posix()
                comp = zipfile.ZIP_STORED if p.suffix in (".gz", ".zst", ".xz", ".jpg", ".png", ".mp4") else zipfile.ZIP_DEFLATED
                zout.write(p, rel, compress_type=comp)

    aligned = BUILD / "aligned.apk"
    run([zipalign, "-f", "4", out, aligned])

    run([apksigner, "sign", "--ks", ks, "--ks-key-alias", cfg.get("alias", "opencode"),
         "--ks-pass", "pass:" + cfg.get("storePassword", "opencode2026"),
         "--key-pass", "pass:" + cfg.get("keyPassword", "opencode2026"),
         "--out", OUT, aligned])
    run([apksigner, "verify", "--verbose", OUT])

    print("\nAPK:", OUT)
    print("size: %.1f MB" % (OUT.stat().st_size / 1048576))
    print("После проверки на телефоне: python tools\\clean_project.py --yes")

if __name__ == "__main__":
    main()

