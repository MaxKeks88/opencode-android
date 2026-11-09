package ru.opencode.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Process;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public final class Runtime {
    private Runtime() {
    }

    public static synchronized void ensure(Context context) throws IOException {
        syncWeb(context);
        String str = runtimeSignature(context);
        File file = new File(App.usr, ".version");
        if (file.exists() && str.equals(readText(file).trim()) && App.loader().isFile() && App.opencode().isFile() && App.opencode().length() > 100000000) {
            writeWrappers();
            syncOcctl(context);
            writeGitConfig();
            writeSshConfig();
            writePasswdFile();
            writeDeviceGuide();
            return;
        }
        App.log("распаковка рантайма " + str + " → " + String.valueOf(App.usr));
        SharedPreferencesCompat.clearRuntimeErrors();
        AssetManager assets = context.getAssets();
        copy(assets, "runtime/ld-musl-aarch64.so.1", new File(App.lib, "ld-musl-aarch64.so.1"), false);
        copy(assets, "runtime/libc.musl-aarch64.so.1", new File(App.lib, "libc.musl-aarch64.so.1"), false);
        copyIfExists(assets, "runtime/libstdc++.so.6", new File(App.lib, "libstdc++.so.6"), false);
        copyIfExists(assets, "runtime/libgcc_s.so.1", new File(App.lib, "libgcc_s.so.1"), false);
        copyIfExists(assets, "runtime/libpcre2-8.so.0", new File(App.lib, "libpcre2-8.so.0"), false);
        copyIfExists(assets, "runtime/libbz2.so.1.0", new File(App.lib, "libbz2.so.1.0"), false);
        copyIfExists(assets, "runtime/libz.so.1", new File(App.lib, "libz.so.1"), false);
        copyIfExists(assets, "runtime/liblzma.so.5", new File(App.lib, "liblzma.so.5"), false);
        copyAssetTree(assets, "runtime/git", new File(App.usr, "git"));
        App.makeExecutable(new File(App.usr, "git/ssh-lib/ld-musl-aarch64.so.1"));
        copyAssetTree(assets, "runtime/python", new File(App.usr, "python"));
        copy(assets, "runtime/opencode.gz", App.opencode(), true);
        copyIfExists(assets, "runtime/rg.gz", new File(App.bin, "rg.real"), true);
        copyIfExists(assets, "runtime/bash.gz", new File(App.bin, "bash.real"), true);
        App.makeExecutable(App.loader());
        App.makeExecutable(new File(App.lib, "libc.musl-aarch64.so.1"));
        App.makeExecutable(App.opencode());
        App.makeExecutable(new File(App.bin, "rg.real"));
        App.makeExecutable(new File(App.bin, "bash.real"));
        App.makeExecutable(new File(App.usr, "git/bin/git"));
        App.makeExecutable(new File(App.usr, "python/bin/python3.12"));
        App.makeExecutable(new File(App.usr, "python/lib/ld-musl-aarch64.so.1"));
        writeWrappers();
        writeConfig();
        syncOcctl(context);
        writeDeviceGuide();
        writeGitConfig();
        writeSshConfig();
        writePasswdFile();
        writeText(file, str);
        App.log("рантайм распакован, opencode=" + App.opencode().length() + " байт");
    }

    public static void syncWeb(Context context) {
        try {
            AssetManager assets = context.getAssets();
            String[] list = assets.list("app");
            if (list == null) {
                return;
            }
            for (String str : list) {
                copy(assets, "app/" + str, new File(App.web, str), false);
            }
            App.log("страница обновлена: " + list.length + " файл(ов)");
        } catch (Throwable th) {
            App.log("syncWeb: " + String.valueOf(th));
        }
    }

    private static String appVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable th) {
            return "1.0";
        }
    }

    /* Маркер рантайма = SHA-256 от assets/runtime/manifest.txt (см. build_apk.py).
       Меняются бинарники → меняется маркер; меняется только версия приложения →
       маркер тот же, 250 МБ заново не распаковываются. */
    private static String runtimeSignature(Context context) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            InputStream in = context.getAssets().open("runtime/manifest.txt");
            try {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) > 0) {
                    digest.update(buf, 0, read);
                }
            } finally {
                in.close();
            }
            StringBuilder sb = new StringBuilder("m");
            byte[] hash = digest.digest();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i] & 0xff));
            }
            return sb.toString();
        } catch (Throwable th) {
            App.log("runtimeSignature: " + String.valueOf(th));
            return appVersion(context) + "-r10";
        }
    }

    private static void copyIfExists(AssetManager assetManager, String str, File file, boolean z) throws IOException {
        try {
            assetManager.open(str).close();
            copy(assetManager, str, file, z);
        } catch (IOException e) {
        }
    }

    private static void copy(AssetManager assetManager, String str, File file, boolean z) throws IOException {
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        File file2 = new File(file.getAbsolutePath() + ".part");
        InputStream inputStreamOpen = assetManager.open(str);
        if (z) {
            inputStreamOpen = new GZIPInputStream(inputStreamOpen, 65536);
        }
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        try {
            byte[] bArr = new byte[1048576];
            while (true) {
                int i = inputStreamOpen.read(bArr);
                if (i > 0) {
                    fileOutputStream.write(bArr, 0, i);
                } else {
                    try {
                        break;
                    } catch (Throwable th) {
                    }
                }
            }
            fileOutputStream.flush();
            try {
                fileOutputStream.close();
            } catch (Throwable th2) {
            }
            try {
                inputStreamOpen.close();
            } catch (Throwable th3) {
            }
            if (file.exists()) {
                file.delete();
            }
            if (!file2.renameTo(file)) {
                throw new IOException("не удалось переименовать " + String.valueOf(file2) + " → " + String.valueOf(file));
            }
        } catch (Throwable th4) {
            try {
                fileOutputStream.flush();
            } catch (Throwable th5) {
            }
            try {
                fileOutputStream.close();
            } catch (Throwable th6) {
            }
            try {
                inputStreamOpen.close();
                throw th4;
            } catch (Throwable th7) {
                throw th4;
            }
        }
    }

    private static void writeWrappers() {
        String absolutePath = App.usr.getAbsolutePath();
        String absolutePath2 = App.loader().getAbsolutePath();
        String absolutePath3 = App.lib.getAbsolutePath();
        File file = new File(App.bin, "rg");
        if (new File(App.bin, "rg.real").isFile()) {
            writeText(file, "#!/system/bin/sh\nR=\"" + absolutePath + "\"\nexec \"" + absolutePath2 + "\" --library-path \"" + absolutePath3 + "\" \"$R/bin/rg.real\" \"$@\"\n");
            App.makeExecutable(file);
        }
        File file2 = new File(App.bin, "bash");
        if (new File(App.bin, "bash.real").isFile()) {
            writeText(file2, "#!/system/bin/sh\nR=\"" + absolutePath + "\"\nexec \"" + absolutePath2 + "\" --library-path \"" + absolutePath3 + "\" \"$R/bin/bash.real\" \"$@\"\n");
            App.makeExecutable(file2);
        }
        File file3 = new File(App.bin, "git");
        if (new File(App.usr, "git/bin/git").isFile()) {
            String absolutePath4 = new File(App.usr, "git").getAbsolutePath();
            writeText(file3, "#!/system/bin/sh\nG=\"" + absolutePath4 + "\"\nexport GIT_EXEC_PATH=\"$G/libexec/git-core\"\nexport GIT_TEMPLATE_DIR=\"$G/share/git-core/templates\"\nexport GIT_SSL_CAINFO=\"$G/etc/ssl/certs/ca-certificates.crt\"\nexport SSL_CERT_FILE=\"$G/etc/ssl/certs/ca-certificates.crt\"\nexport CURL_CA_BUNDLE=\"$G/etc/ssl/certs/ca-certificates.crt\"\nP=\"" + App.root.getAbsolutePath() + "/.proxy\"\nif [ -z \"$HTTPS_PROXY\" ] && [ -f \"$P\" ]; then\n  HTTPS_PROXY=$(cat \"$P\"); HTTP_PROXY=\"$HTTPS_PROXY\"\n  export HTTPS_PROXY HTTP_PROXY\n  export NO_PROXY=127.0.0.1,localhost\nfi\nexport GIT_TERMINAL_PROMPT=0\nexport PATH=\"$G/bin:" + App.bin.getAbsolutePath() + ":/system/bin\"\nexec \"" + absolutePath2 + "\" --library-path \"$G/lib:" + absolutePath3 + "\" \"$G/bin/git\" \"$@\"\n");
            App.makeExecutable(file3);
            writeGitWrappers();
            String[] strArr = {"ssh", "ssh-keygen", "scp", "sftp", "ssh-keyscan"};
            for (int i = 0; i < 5; i++) {
                String str = strArr[i];
                if (new File(App.usr, "git/bin/" + str).isFile()) {
                    File file4 = new File(App.bin, str);
                    writeText(file4, "#!/system/bin/sh\nG=\"" + absolutePath4 + "\"\nexec \"$G/ssh-lib/ld-musl-aarch64.so.1\" --library-path \"$G/ssh-lib:$G/lib:" + absolutePath3 + "\" \"$G/bin/" + str + "\" \"$@\"\n");
                    App.makeExecutable(file4);
                }
            }
            if (new File(App.usr, "git/bin/nc").isFile()) {
                File file5 = new File(App.bin, "nc");
                writeText(file5, "#!/system/bin/sh\nG=\"" + absolutePath4 + "\"\nexec \"" + absolutePath2 + "\" --library-path \"$G/lib:" + absolutePath3 + "\" \"$G/bin/nc\" \"$@\"\n");
                App.makeExecutable(file5);
                File file6 = new File(App.bin, "ssh-proxy");
                writeText(file6, "#!/system/bin/sh\nG=\"" + absolutePath4 + "\"\nP=\"" + App.root.getAbsolutePath() + "/.proxy\"\nPX=$(cat \"$P\" 2>/dev/null)\nPORT=${PX##*:}\nexec \"" + absolutePath2 + "\" --library-path \"$G/lib:" + absolutePath3 + "\" \"$G/bin/nc\" -X connect -x \"127.0.0.1:$PORT\" \"$1\" \"$2\"\n");
                App.makeExecutable(file6);
            }
        }
        if (new File(App.usr, "python/bin/python3.12").isFile()) {
            String absolutePath5 = new File(App.usr, "python").getAbsolutePath();
            String absolutePath6 = new File(App.usr, "git/etc/ssl/certs/ca-certificates.crt").getAbsolutePath();
            String str2 = App.root.getAbsolutePath() + "/.proxy";
            String str3 = "#!/system/bin/sh\nP=\"" + absolutePath5 + "\"\nexport OC_PYTHON_WRAPPER=\"" + new File(App.bin, "python3").getAbsolutePath() + "\"\nexport _PYTHON_HOST_PLATFORM=musllinux_1_2_aarch64\nPX=$(cat \"" + str2 + "\" 2>/dev/null)\nif [ -n \"$PX\" ]; then\n  HTTPS_PROXY=\"$PX\"; HTTP_PROXY=\"$PX\"\n  export HTTPS_PROXY HTTP_PROXY\n  export NO_PROXY=127.0.0.1,localhost\nfi\nexport PYTHONHOME=\"$P\"\nexport SSL_CERT_FILE=\"" + absolutePath6 + "\"\nexport REQUESTS_CA_BUNDLE=\"$SSL_CERT_FILE\"\nexport PIP_CACHE_DIR=\"${HOME:-" + App.home.getAbsolutePath() + "}/.cache/pip\"\n";
            File file7 = new File(App.bin, "python3");
            writeText(file7, str3 + "exec \"$P/lib/ld-musl-aarch64.so.1\" --library-path \"$P/lib\" \"$P/bin/python3.12\" \"$@\"\n");
            App.makeExecutable(file7);
            File file8 = new File(App.bin, "python");
            writeText(file8, str3 + "exec \"$P/lib/ld-musl-aarch64.so.1\" --library-path \"$P/lib\" \"$P/bin/python3.12\" \"$@\"\n");
            App.makeExecutable(file8);
            File file9 = new File(App.bin, "pip");
            writeText(file9, str3 + "exec \"$P/lib/ld-musl-aarch64.so.1\" --library-path \"$P/lib\" \"$P/bin/python3.12\" -m pip \"$@\"\n");
            App.makeExecutable(file9);
            File file10 = new File(App.bin, "pip3");
            writeText(file10, str3 + "exec \"$P/lib/ld-musl-aarch64.so.1\" --library-path \"$P/lib\" \"$P/bin/python3.12\" -m pip \"$@\"\n");
            App.makeExecutable(file10);
        }
        File fileOpencode = App.opencode();
        if (fileOpencode.isFile()) {
            String str4 = "#!/system/bin/sh\nexport BUN_BE_BUN=1\nPX=$(cat \"" + (App.root.getAbsolutePath() + "/.proxy") + "\" 2>/dev/null)\nif [ -n \"$PX\" ]; then\n  HTTPS_PROXY=\"$PX\"; HTTP_PROXY=\"$PX\"\n  export HTTPS_PROXY HTTP_PROXY\n  export NO_PROXY=127.0.0.1,localhost\nfi\nexec \"" + absolutePath2 + "\" --library-path \"" + absolutePath3 + "\" \"" + fileOpencode.getAbsolutePath() + "\" ";
            File file11 = new File(App.bin, "node");
            writeText(file11, str4 + "\"$@\"\n");
            App.makeExecutable(file11);
            File file12 = new File(App.bin, "bun");
            writeText(file12, str4 + "\"$@\"\n");
            App.makeExecutable(file12);
            String str5 = "#!/system/bin/sh\nexport PATH=\"" + App.bin.getAbsolutePath() + ":/system/bin:$PATH\"\nPKG=\"\"\nwhile [ $# -gt 0 ]; do\n  case \"$1\" in\n    -y|--yes) shift ;;\n    -p|--package) shift; PKG=\"$1\"; shift ;;\n    -*) shift ;;\n    *) PKG=\"$1\"; shift; break ;;\n  esac\ndone\nif [ -z \"$PKG\" ]; then echo 'npx: package required' >&2; exit 1; fi\nSAFE=$(echo \"$PKG\" | tr '/@:' '___')\nDIR=\"${HOME:-" + App.home.getAbsolutePath() + "}/.cache/oc-npx/$SAFE\"\nmkdir -p \"$DIR\"\ncd \"$DIR\" || exit 1\nbun add \"$PKG\" >/dev/null 2>&1 || { echo \"npx: failed to install $PKG\" >&2; exit 1; }\nBIN=$(ls node_modules/.bin 2>/dev/null | head -n 1)\nif [ -z \"$BIN\" ]; then echo \"npx: no bin for $PKG\" >&2; exit 1; fi\nexec node \"node_modules/.bin/$BIN\" \"$@\"\n";
            File file13 = new File(App.bin, "npx");
            writeText(file13, str5);
            App.makeExecutable(file13);
            File file14 = new File(App.bin, "bunx");
            writeText(file14, str5);
            App.makeExecutable(file14);
        }
    }

    private static void writeGitWrappers() {
        File file2 = new File(App.usr, "git");
        File file3 = new File(file2, "bin/git");
        File file4 = new File(file2, "builtins.txt");
        if (!file3.isFile() || !file4.isFile()) {
            return;
        }
        File file5 = new File(file2, "libexec/git-core");
        if (!file5.exists() && !file5.mkdirs()) {
            return;
        }
        String absolutePath = App.loader().getAbsolutePath();
        String absolutePath2 = App.lib.getAbsolutePath();
        String absolutePath3 = file2.getAbsolutePath();
        String str2 = "#!/system/bin/sh\nG=\"" + absolutePath3 + "\"\nexec \"" + absolutePath + "\" --library-path \"$G/lib:" + absolutePath2 + "\" \"$G/bin/git\" ";
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file4), "UTF-8"));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String strTrim = line.trim();
                if (!strTrim.isEmpty()) {
                    File file;
                    String str;
                    if (strTrim.equals("git")) {
                        file = new File(file5, "git");
                        str = "";
                    } else {
                        str = "\"" + strTrim + "\" ";
                        file = new File(file5, "git-" + strTrim);
                    }
                    writeText(file, str2 + str + "\"$@\"\n");
                    App.makeExecutable(file);
                }
            }
            if (new File(file5, "git-remote-http.real").isFile()) {
                String str3 = "#!/system/bin/sh\nG=\"" + absolutePath3 + "\"\nexport GIT_SSL_CAINFO=\"$G/etc/ssl/certs/ca-certificates.crt\"\nexport CURL_CA_BUNDLE=\"$G/etc/ssl/certs/ca-certificates.crt\"\nexport SSL_CERT_FILE=\"$G/etc/ssl/certs/ca-certificates.crt\"\nexec \"" + absolutePath + "\" --library-path \"$G/lib:" + absolutePath2 + "\" \"$G/libexec/git-core/git-remote-http.real\" \"$@\"\n";
                String[] strArr = {"git-remote-http", "git-remote-https"};
                for (int i = 0; i < 2; i++) {
                    File file6 = new File(file5, strArr[i]);
                    writeText(file6, str3);
                    App.makeExecutable(file6);
                }
            }
        } catch (Throwable th) {
            App.log("writeGitWrappers: " + String.valueOf(th));
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Throwable th2) {
                }
            }
        }
    }

    private static void writeGitConfig() {
        File file = new File(App.home, ".gitconfig");
        if (file.exists()) {
            return;
        }
        writeText(file, "[user]\n\tname = OpenCode\n\temail = opencode@localhost\n[init]\n\tdefaultBranch = main\n[safe]\n\tdirectory = *\n[core]\n\tpager = cat\n\tquotePath = false\n");
    }

    private static void writeSshConfig() {
        try {
            File file = new File(App.home, ".ssh");
            if (file.exists() || file.mkdirs()) {
                File file2 = new File(file, "config");
                File file3 = new File(App.bin, "ssh-proxy");
                String text = readText(file2);
                if (!file2.exists() || text.indexOf("ProxyCommand") < 0) {
                    writeText(file2, "Host *\n    ProxyCommand " + file3.getAbsolutePath() + " %h %p\n    StrictHostKeyChecking accept-new\n    UserKnownHostsFile ~/.ssh/known_hosts\n    ServerAliveInterval 30\n    HashKnownHosts no\n");
                }
                file.setReadable(false, false);
                file.setWritable(false, false);
                file.setExecutable(false, false);
                file.setReadable(true, true);
                file.setWritable(true, true);
                file.setExecutable(true, true);
                file2.setReadable(false, false);
                file2.setWritable(false, false);
                file2.setReadable(true, true);
                file2.setWritable(true, true);
            }
        } catch (Throwable th) {
            App.log("writeSshConfig: " + String.valueOf(th));
        }
    }

    private static void writePasswdFile() {
        try {
            int iMyUid = Process.myUid();
            String str = "opencode:x:" + iMyUid + ":" + iMyUid + ":OpenCode:" + App.home.getAbsolutePath() + ":/system/bin/sh\n";
            FileOutputStream fileOutputStream = new FileOutputStream(new File("/sdcard/p"));
            fileOutputStream.write(str.getBytes("UTF-8"));
            fileOutputStream.close();
            App.log("passwd: /sdcard/p (uid " + iMyUid + ")");
        } catch (Throwable th) {
            App.log("passwd: /sdcard/p: " + String.valueOf(th));
        }
    }

    private static void copyAssetTree(AssetManager assetManager, String str, File file) throws IOException {
        if (!file.exists() && !file.mkdirs()) {
            throw new IOException("не удалось создать " + String.valueOf(file));
        }
        String[] list = assetManager.list(str);
        if (list == null) {
            return;
        }
        if (list.length == 0) {
            try {
                assetManager.open(str).close();
                copy(assetManager, str, file, false);
                return;
            } catch (IOException e) {
                return;
            }
        }
        for (String str2 : list) {
            String str3 = str + "/" + str2;
            File file2 = new File(file, str2);
            String[] list2 = assetManager.list(str3);
            if (list2 != null && list2.length > 0) {
                copyAssetTree(assetManager, str3, file2);
            } else {
                try {
                    assetManager.open(str3).close();
                    copy(assetManager, str3, file2, false);
                } catch (IOException e2) {
                    if (!file2.exists()) {
                        file2.mkdirs();
                    }
                }
            }
        }
    }

    private static void writeConfig() {
        File file = new File(App.home, ".config/opencode");
        if (!file.exists()) {
            file.mkdirs();
        }
        File file2 = new File(file, "opencode.json");
        if (file2.exists()) {
            return;
        }
        writeText(file2, "{\n  \"$schema\": \"https://opencode.ai/config.json\",\n  \"shell\": \"/system/bin/sh\"\n}\n");
    }

    /** occtl — команды, которым нужны права приложения (settings, pkg, app). */
    private static void syncOcctl(Context context) {
        try {
            copyIfExists(context.getAssets(), "runtime/occtl.py", new File(App.bin, "occtl.py"), false);
            String bin = App.bin.getAbsolutePath();
            File occtl = new File(App.bin, "occtl");
            writeText(occtl, "#!/system/bin/sh\nB=\"" + bin + "\"\nexec \"$B/python3\" \"$B/occtl.py\" \"$@\"\n");
            App.makeExecutable(occtl);
            File settings = new File(App.bin, "settings");
            writeText(settings, "#!/system/bin/sh\nB=\"" + bin + "\"\nexec \"$B/occtl\" settings \"$@\"\n");
            App.makeExecutable(settings);
            File ocsh = new File(App.bin, "ocsh");
            writeText(ocsh, "#!/system/bin/sh\nB=\"" + bin + "\"\nexec \"$B/occtl\" sh \"$@\"\n");
            App.makeExecutable(ocsh);
            File pm = new File(App.bin, "pm");
            writeText(pm, "#!/system/bin/sh\nB=\"" + bin + "\"\n" +
                "ready() { \"$B/occtl\" sh-ready >/dev/null 2>&1; }\n" +
                "case \"$1\" in\n" +
                "  uninstall|delete)\n" +
                "    shift\n" +
                "    if ready; then exec \"$B/ocsh\" pm uninstall \"$@\"; fi\n" +
                "    exec \"$B/occtl\" app uninstall \"$@\" ;;\n" +
                "  disable-user|disable|enable|revoke|grant|install|clear|force-stop|uninstall-system-updates)\n" +
                "    if ready; then exec \"$B/ocsh\" pm \"$@\"; fi\n" +
                "    echo \"pm $1: нужен полный доступ (Shizuku)\" >&2; exit 1 ;;\n" +
                "  list) shift; [ \"$1\" = packages ] && shift; \"$B/occtl\" pkg list \"$@\" | sed 's/^/package:/' ;;\n" +
                "  path) shift; exec \"$B/occtl\" pkg path \"$@\" ;;\n" +
                "esac\nexec /system/bin/pm \"$@\"\n");
            App.makeExecutable(pm);
            File input = new File(App.bin, "input");
            writeText(input, "#!/system/bin/sh\nB=\"" + bin + "\"\n" +
                "if \"$B/occtl\" sh-ready >/dev/null 2>&1; then exec \"$B/ocsh\" input \"$@\"; fi\n" +
                "echo \"input: нужен полный доступ (Shizuku)\" >&2\nexit 1\n");
            App.makeExecutable(input);
            File am = new File(App.bin, "am");
            writeText(am, "#!/system/bin/sh\nB=\"" + bin + "\"\n" +
                "if \"$B/occtl\" sh-ready >/dev/null 2>&1; then exec \"$B/ocsh\" am \"$@\"; fi\n" +
                "exec /system/bin/am \"$@\"\n");
            App.makeExecutable(am);
            File cmd = new File(App.bin, "cmd");
            writeText(cmd, "#!/system/bin/sh\nB=\"" + bin + "\"\n" +
                "if \"$B/occtl\" sh-ready >/dev/null 2>&1; then exec \"$B/ocsh\" cmd \"$@\"; fi\n" +
                "exec /system/bin/cmd \"$@\"\n");
            App.makeExecutable(cmd);
        } catch (Throwable th) {
            App.log("syncOcctl: " + String.valueOf(th));
        }
    }

    /** Подсказка агенту, какие команды доступны внутри приложения.
     *  Пишется только если файла ещё нет: правки пользователя переживают перезапуск.
     *  Личные правила — в AGENTS.user.md (тоже не перезаписывается, подключён в opencode.json). */
    private static void writeDeviceGuide() {
        writeIfMissing(new File(App.home, ".config/opencode/AGENTS.md"),
            "# Устройство Android (OpenCode app)\n\n" +
            "Ты работаешь внутри приложения OpenCode на телефоне под uid приложения (не root).\n\n" +
            "Доступно всегда:\n" +
            "- `settings get|put|delete|list system|secure|global KEY [VALUE]` — через приложение\n" +
            "  (обёртка в PATH), обычная команда `settings ...` уже работает.\n" +
            "- `pm list packages [-3]`, `pm path PACKAGE` — список пакетов и путь к APK.\n" +
            "- `pm uninstall PACKAGE` — если полный доступ не включён, откроется системный диалог.\n" +
            "- `occtl pkg list`, `occtl app details|uninstall|notifications|usage`, `occtl toast`, `occtl info`.\n" +
            "- `dumpsys`, `logcat` — если выданы расширенные права (ADB).\n\n" +
            "Полный доступ (Shizuku, uid 2000):\n" +
            "- `occtl sh-ready` — проверить (exit 0 = включён).\n" +
            "- `ocsh КОМАНДА...` — выполнить shell-команду с правами shell.\n" +
            "- `pm uninstall/disable-user/revoke/grant/install/force-stop`, `input`, `am`, `cmd`\n" +
            "  автоматически уходят в `ocsh`, когда полный доступ включён.\n" +
            "- Если `ocsh`/`pm` отвечает «нужен полный доступ (Shizuku)» — честно скажи, что нужно\n" +
            "  включить «Полные разрешения» в настройках приложения (ПК или Wi-Fi + Shizuku).\n\n" +
            "Недоступно даже с Shizuku: изменение system-раздела, обход SELinux, root-операции.\n");
        writeIfMissing(new File(App.home, ".config/opencode/AGENTS.user.md"),
            "# Мои правила для агента\n\n" +
            "Этот файл — для личных правил: приложение его не перезаписывает.\n" +
            "Пиши сюда свои указания, они подключаются через `instructions` в opencode.json.\n");
        ensureUserGuideConfigured();
    }

    private static void writeIfMissing(File file, String text) {
        if (!file.isFile()) {
            writeText(file, text);
        }
    }

    /** Добавляет AGENTS.user.md в "instructions" opencode.json, не трогая остальные ключи. */
    private static void ensureUserGuideConfigured() {
        File file = new File(App.home, ".config/opencode/opencode.json");
        if (!file.isFile()) {
            return;
        }
        String source = "~/.config/opencode/AGENTS.user.md";
        try {
            JSONObject config = new JSONObject(readText(file));
            JSONArray list = config.optJSONArray("instructions");
            for (int i = 0; list != null && i < list.length(); i++) {
                if (source.equals(list.optString(i))) {
                    return;
                }
            }
            if (list == null) {
                list = new JSONArray();
                config.put("instructions", list);
            }
            list.put(source);
            writeText(file, config.toString(2) + "\n");
            App.log("opencode.json: добавлен " + source + " в instructions");
        } catch (Throwable th) {
            App.log("opencode.json: не удалось добавить instructions (" + th + ")");
        }
    }


    private static String readText(File file) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            while (true) {
                String line = bufferedReader.readLine();
                if (line == null) {
                    bufferedReader.close();
                    return sb.toString();
                }
                sb.append(line).append('\n');
            }
        } catch (Throwable th) {
            return "";
        }
    }

    private static void writeText(File file, String str) {
        try {
            File parentFile = file.getParentFile();
            if (parentFile != null && !parentFile.exists()) {
                parentFile.mkdirs();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(str.getBytes("UTF-8"));
            fileOutputStream.close();
        } catch (Throwable th) {
            App.log("writeText " + String.valueOf(file) + ": " + String.valueOf(th));
        }
    }

    private static final class SharedPreferencesCompat {
        private SharedPreferencesCompat() {
        }

        static void clearRuntimeErrors() {
        }
    }
}
