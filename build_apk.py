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

def keystore():
    ks = BASE / "keystore.jks"
    props = BASE / "keystore.properties"
    if not props.exists():
        props.write_text("storePassword=opencode2026\nkeyPassword=opencode2026\nalias=opencode\n",
                         encoding="utf-8")
    cfg = {}
    for line in props.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            cfg[k.strip()] = v.strip()
    if not ks.exists():
        run([KEYTOOL, "-genkeypair", "-keystore", ks, "-alias", cfg.get("alias", "opencode"),
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
             "-storepass", cfg.get("storePassword", "opencode2026"),
             "-keypass", cfg.get("keyPassword", "opencode2026"),
             "-dname", "CN=OpenCode Android, O=OpenCode, C=RU"])
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

def main():
    aapt2, zipalign, apksigner, d8, android_jar = find_tools()
    print("SDK:", SDK)
    print("android.jar:", android_jar)

    force_rmtree(BUILD)
    (BUILD / "gen").mkdir(parents=True)
    (BUILD / "classes").mkdir()
    (BUILD / "dex").mkdir()

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

    ks, cfg = keystore()
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

