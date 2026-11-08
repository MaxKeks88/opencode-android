package ru.opencode.android;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public final class Updater {
    private static final String ASSET = "opencode-linux-arm64-musl.tar.gz";
    private static final String REPO = "anomalyco/opencode";

    public interface Callback {
        void done(String str);
    }

    private Updater() {
    }

    public static JSONObject latestV2() throws Exception {
        JSONArray jSONArray = new JSONArray(get("https://api.github.com/repos/anomalyco/opencode/releases?per_page=50"));
        JSONObject jSONObject = null;
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject jSONObjectOptJSONObject = jSONArray.optJSONObject(i);
            if (jSONObjectOptJSONObject != null) {
                String strOptString = jSONObjectOptJSONObject.optString("tag_name", "");
                if (strOptString.matches("v2\\.\\d+\\.\\d+") && (jSONObject == null || isNewer(strOptString, jSONObject.optString("tag_name")))) {
                    jSONObject = jSONObjectOptJSONObject;
                }
            }
        }
        if (jSONObject == null) {
            throw new Exception("нет релизов v2.x");
        }
        return jSONObject;
    }

    public static boolean isNewer(String str, String str2) {
        int[] iArrVer = ver(str);
        int[] iArrVer2 = ver(str2);
        for (int i = 0; i < 3; i++) {
            if (iArrVer[i] != iArrVer2[i]) {
                return iArrVer[i] > iArrVer2[i];
            }
        }
        return false;
    }

    private static int[] ver(String str) {
        if (str.startsWith("v")) {
            str = str.substring(1);
        }
        String[] strArrSplit = str.split("\\.");
        int[] iArr = new int[3];
        for (int i = 0; i < 3 && i < strArrSplit.length; i++) {
            try {
                iArr[i] = Integer.parseInt(strArrSplit[i].replaceAll("[^0-9].*$", ""));
            } catch (Exception e) {
                iArr[i] = 0;
            }
        }
        return iArr;
    }

    private static String get(String str) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setConnectTimeout(15000);
        httpURLConnection.setReadTimeout(30000);
        httpURLConnection.setRequestProperty("User-Agent", "OpenCodeAndroid");
        httpURLConnection.setRequestProperty("Accept", "application/vnd.github+json");
        int responseCode = httpURLConnection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode);
        }
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String line = bufferedReader.readLine();
            if (line == null) {
                bufferedReader.close();
                httpURLConnection.disconnect();
                return sb.toString();
            }
            sb.append(line);
        }
    }

    public static void downloadAndInstall(final Context context, final JSONObject jSONObject, final Callback callback) {
        new Thread(new Runnable() { // from class: ru.opencode.android.Updater$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                Updater.lambda$downloadAndInstall$0(jSONObject, context, callback);
            }
        }, "oc-update").start();
    }

    static /* synthetic */ void lambda$downloadAndInstall$0(JSONObject jSONObject, Context context, Callback callback) {
        String str;
        String strOptString;
        File file = new File(App.bin, "opencode.bak");
        File fileOpencode = App.opencode();
        File file2 = new File(App.tmp, "opencode-update.tar.gz");
        File file3 = new File(App.tmp, "opencode.new");
        try {
            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("assets");
            if (jSONArrayOptJSONArray != null) {
                int i = 0;
                while (true) {
                    if (i < jSONArrayOptJSONArray.length()) {
                        JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i);
                        if (jSONObjectOptJSONObject != null && ASSET.equals(jSONObjectOptJSONObject.optString("name"))) {
                            strOptString = jSONObjectOptJSONObject.optString("browser_download_url");
                            break;
                        }
                        i++;
                    } else {
                        strOptString = null;
                        break;
                    }
                }
            } else {
                strOptString = null;
            }
            if (strOptString == null) {
                throw new Exception("нет ассета opencode-linux-arm64-musl.tar.gz");
            }
            download(strOptString, file2);
            extractOpencode(file2, file3);
            FileInputStream fileInputStream = new FileInputStream(file3);
            byte[] bArr = new byte[4];
            int i2 = fileInputStream.read(bArr);
            fileInputStream.close();
            if (i2 != 4 || bArr[0] != 127 || bArr[1] != 69 || bArr[2] != 76 || bArr[3] != 70) {
                throw new Exception("скачанный файл не ELF");
            }
            if (file3.length() < 50000000) {
                throw new Exception("бинарник слишком мал");
            }
            RuntimeService.stop(context);
            Thread.sleep(2500L);
            if (fileOpencode.exists()) {
                if (file.exists()) {
                    file.delete();
                }
                if (!fileOpencode.renameTo(file)) {
                    throw new Exception("не удалось сделать бэкап");
                }
            }
            if (!file3.renameTo(fileOpencode)) {
                if (file.exists()) {
                    file.renameTo(fileOpencode);
                }
                throw new Exception("не удалось заменить бинарник");
            }
            App.makeExecutable(fileOpencode);
            RuntimeService.start(context);
            str = "Обновлено. OpenCode перезапускается.";
            try {
                file2.delete();
            } catch (Throwable th) {
            }
            callback.done(str);
        } catch (Throwable th2) {
            try {
                str = "Ошибка обновления: " + String.valueOf(th2);
                try {
                    RuntimeService.start(context);
                } catch (Throwable th3) {
                }
                try {
                    file2.delete();
                } catch (Throwable th4) {
                }
            } catch (Throwable th5) {
                try {
                    file2.delete();
                } catch (Throwable th6) {
                }
                callback.done("Обновление прервано");
                throw th5;
            }
        }
    }

    private static void download(String str, File file) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setConnectTimeout(20000);
        httpURLConnection.setReadTimeout(600000);
        httpURLConnection.setRequestProperty("User-Agent", "OpenCodeAndroid");
        httpURLConnection.setInstanceFollowRedirects(true);
        int responseCode = httpURLConnection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode);
        }
        InputStream inputStream = httpURLConnection.getInputStream();
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        byte[] bArr = new byte[1048576];
        while (true) {
            int i = inputStream.read(bArr);
            if (i <= 0) {
                inputStream.close();
                fileOutputStream.close();
                httpURLConnection.disconnect();
                return;
            }
            fileOutputStream.write(bArr, 0, i);
        }
    }

    private static void extractOpencode(File file, File file2) throws Exception {
        boolean z;
        GZIPInputStream gZIPInputStream = new GZIPInputStream(new BufferedInputStream(new FileInputStream(file)));
        byte[] bArr = new byte[512];
        byte[] bArr2 = new byte[1048576];
        while (readFully(gZIPInputStream, bArr) >= 512) {
            try {
                int i = 0;
                while (true) {
                    if (i >= 512) {
                        z = true;
                        break;
                    } else {
                        if (bArr[i] != 0) {
                            z = false;
                            break;
                        }
                        i++;
                    }
                }
                if (z) {
                    break;
                }
                String strTrim = new String(bArr, 0, 100, "UTF-8").trim();
                long octal = parseOctal(bArr, 124, 12);
                int i2 = bArr[156] & 255;
                if ((i2 == 0 || i2 == 48) && (strTrim.equals(App.PREFS) || strTrim.equals("./opencode") || strTrim.endsWith("/opencode"))) {
                    FileOutputStream fileOutputStream = new FileOutputStream(file2);
                    copyExactly(gZIPInputStream, fileOutputStream, octal, bArr2);
                    fileOutputStream.close();
                    try {
                        gZIPInputStream.close();
                        return;
                    } catch (Throwable th) {
                        return;
                    }
                }
                skipExactly(gZIPInputStream, octal);
                skipExactly(gZIPInputStream, (512 - (octal % 512)) % 512);
            } catch (Throwable th2) {
                try {
                    gZIPInputStream.close();
                } catch (Throwable th3) {
                }
                throw th2;
            }
        }
        throw new Exception("opencode не найден в архиве");
    }

    private static int readFully(InputStream inputStream, byte[] bArr) throws IOException {
        int i;
        int i2 = 0;
        while (i2 < bArr.length && (i = inputStream.read(bArr, i2, bArr.length - i2)) >= 0) {
            i2 += i;
        }
        return i2;
    }

    private static void skipExactly(InputStream inputStream, long j) throws IOException {
        while (j > 0) {
            long jSkip = inputStream.skip(j);
            if (jSkip <= 0) {
                if (inputStream.read() < 0) {
                    return;
                } else {
                    jSkip = 1;
                }
            }
            j -= jSkip;
        }
    }

    private static void copyExactly(InputStream inputStream, OutputStream outputStream, long j, byte[] bArr) throws IOException {
        int i;
        while (j > 0 && (i = inputStream.read(bArr, 0, (int) Math.min(bArr.length, j))) >= 0) {
            outputStream.write(bArr, 0, i);
            j -= (long) i;
        }
    }

    private static long parseOctal(byte[] bArr, int i, int i2) {
        int i3 = i2 + i;
        while (i < i3 && (bArr[i] == 32 || bArr[i] == 0)) {
            i++;
        }
        long j = 0;
        while (i < i3 && bArr[i] >= 48 && bArr[i] <= 55) {
            j = (j * 8) + ((long) (bArr[i] - 48));
            i++;
        }
        return j;
    }
}
