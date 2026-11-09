#!/usr/bin/env python3
"""Смоук-тест приложения OpenCode Android по локальному HTTP.

Запуск (телефон подключён по ADB):
    python tools/smoke_test.py            # быстрые проверки, ~10 секунд
    python tools/smoke_test.py --deep     # + отправка сообщения и уведомление

Скрипт сам берёт серийник телефона, токен из shared_prefs (ничего не хранит
в репозитории), поднимает adb forward на свободный порт и всё проверяет.
"""
import argparse
import http.client
import json
import re
import socket
import subprocess
import sys
import time

PKG = "ru.opencode.android"
LOCAL_PORT = 18799


def adb(serial, *args, timeout=60):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    return subprocess.run(cmd, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=timeout)


def first_device():
    out = adb(None, "devices").stdout
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            return parts[0]
    return None


def read_token(serial):
    out = adb(serial, "shell", "run-as", PKG, "cat",
              "shared_prefs/opencode.xml").stdout
    m = re.search(r'name="local_token"[^>]*>([^<]+)<', out)
    if not m:
        raise SystemExit("не нашёл local_token в shared_prefs (приложение "
                         "запускалось? данные на месте?)")
    return m.group(1)


def http_get(port, path, token=None, timeout=30):
    c = http.client.HTTPConnection("127.0.0.1", port, timeout=timeout)
    headers = {"X-Bridge-Token": token} if token else {}
    c.request("GET", path, headers=headers)
    r = c.getresponse()
    body = r.read()
    c.close()
    return r.status, body


def http_json(port, method, path, body=None, token=None, timeout=60):
    c = http.client.HTTPConnection("127.0.0.1", port, timeout=timeout)
    headers = {"X-Bridge-Token": token} if token else {}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    c.request(method, path, body=data, headers=headers)
    r = c.getresponse()
    raw = r.read()
    c.close()
    try:
        return r.status, json.loads(raw.decode("utf-8"))
    except Exception:
        return r.status, raw


def raw_request(port, payload, timeout=8):
    s = socket.create_connection(("127.0.0.1", port), timeout=timeout)
    s.sendall(payload)
    s.settimeout(timeout)
    data = b""
    try:
        while True:
            b = s.recv(65536)
            if not b:
                break
            data += b
            if b"\r\n\r\n" in data and len(data) > 20:
                break
    except socket.timeout:
        pass
    s.close()
    return int(data.split(b" ")[1]) if b" " in data else None


def notifications(serial):
    out = adb(serial, "shell", "dumpsys", "notification", "--noredact").stdout
    res = []
    for block in out.split("NotificationRecord("):
        if PKG in block and "id=1001" in block:
            m = re.search(r"when=(\d+)/", block)
            res.append((m.group(1) if m else None, "Ответ готов" in block))
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--deep", action="store_true",
                    help="дополнительно: отправить сообщение и проверить уведомление")
    ap.add_argument("--serial", default=None)
    ap.add_argument("--port", type=int, default=LOCAL_PORT)
    args = ap.parse_args()

    serial = args.serial or first_device()
    if not serial:
        raise SystemExit("телефон не найден (adb devices)")
    print("телефон:", serial)

    token = read_token(serial)
    adb(serial, "forward", "tcp:%d" % args.port, "tcp:8769")
    port = args.port
    ok = True

    def check(name, cond, extra=""):
        nonlocal ok
        ok = ok and bool(cond)
        print(("OK   " if cond else "FAIL ") + name + ("  " + str(extra) if extra else ""))

    # 1. приложение живо и версия видна
    st, body = http_get(port, "/ping")
    ping = {}
    try:
        ping = json.loads(body.decode("utf-8"))
    except Exception:
        pass
    check("/ping → 200 и online", st == 200 and ping.get("online") is True,
          "version=%s" % ping.get("version"))

    # 2. без токена нельзя
    st, _ = http_get(port, "/oc/api/info")
    check("/oc/api/info без токена → 403", st == 403, "получено %s" % st)

    # 3. с токеном можно
    st, info = http_json(port, "GET", "/oc/api/info", token=token)
    check("/oc/api/info с токеном → 200", st == 200,
          "opencode %s" % (info.get("version") if isinstance(info, dict) else ""))

    # 4. /files без токена закрыт
    st, _ = http_get(port, "/files/%2Fsdcard%2Fp")
    check("/files без токена → 403", st == 403, "получено %s" % st)

    # 5. лимиты тела: аноним с огромным Content-Length → 403 (без чтения тела)
    st = raw_request(port, ("POST /oc/api/session HTTP/1.1\r\nHost: 127.0.0.1\r\n"
                            "Content-Length: 200000000\r\n\r\n").encode())
    check("аноним с телом 200 МБ → 403", st == 403, "получено %s" % st)

    # 6. с токеном → 413
    st = raw_request(port, ("POST /oc/api/session HTTP/1.1\r\nHost: 127.0.0.1\r\n"
                            "X-Bridge-Token: %s\r\n"
                            "Content-Length: 200000000\r\n\r\n" % token).encode())
    check("с токеном тело 200 МБ → 413", st == 413, "получено %s" % st)

    # 7. каталог провайдеров
    st, cat = http_json(port, "GET", "/oc/api/integration", token=token, timeout=60)
    items = (cat.get("data") if isinstance(cat, dict) else cat) or []
    check("каталог провайдеров", st == 200 and len(items) > 100,
          "%d провайдеров" % len(items))

    # 8. модели
    st, models = http_json(port, "GET", "/oc/api/model", token=token, timeout=90)
    mlist = (models.get("data") if isinstance(models, dict) else models) or []
    check("список моделей", st == 200 and len(mlist) > 0, "%d моделей" % len(mlist))

    if args.deep:
        # 9. сообщение: новая сессия → ответ ассистента
        st, ses = http_json(port, "POST", "/oc/api/session",
                            {"location": {"directory": "/storage/emulated/0/OpenCode"}},
                            token=token, timeout=60)
        sid = ((ses.get("data") or ses) if isinstance(ses, dict) else {}).get("id")
        check("создание сессии", st == 200 and bool(sid), sid)
        if sid:
            model = None
            for m in mlist:
                if m.get("providerID") == "opencode-go":
                    model = m
                    break
            if model is None and mlist:
                model = mlist[0]
            if model:
                http_json(port, "POST", "/oc/api/session/%s/model" % sid,
                          {"model": {"id": model.get("id"),
                                     "providerID": model.get("providerID"),
                                     "variant": "default"}}, token=token)
            before = notifications(serial)
            http_json(port, "POST", "/oc/api/session/%s/prompt" % sid,
                      {"text": "Ответь одним словом: тест."}, token=token, timeout=90)
            adb(serial, "shell", "input", "keyevent", "KEYCODE_HOME")
            done = False
            for _ in range(90):
                time.sleep(2)
                st, sess = http_json(port, "GET", "/oc/api/session/%s" % sid, token=token)
                d = (sess.get("data") or {}) if isinstance(sess, dict) else {}
                t = d.get("time") or {}
                if t.get("idle", 0) and t.get("idle", 0) >= t.get("busy", 0):
                    done = True
                    break
            check("ответ получен", done, "outcome=%s" % d.get("outcome"))

            st, msgs = http_json(port, "GET", "/oc/api/session/%s/message" % sid,
                                 token=token, timeout=60)
            text = ""
            for m in ((msgs.get("data") if isinstance(msgs, dict) else msgs) or []):
                for part in (m.get("content") or []):
                    if part.get("type") == "text":
                        text += part.get("text") or ""
            check("ассистент ответил текстом", len(text.strip()) > 0, text.strip()[:60])

            time.sleep(4)
            after = notifications(serial)
            old = {w for w, _ in before}
            fresh = [x for x in after if x[0] not in old and x[1]]
            check("одно новое уведомление «Ответ готов»", len(fresh) == 1,
                  "новых: %d (всего записей %d)" % (len(fresh), len(after)))

            http_json(port, "DELETE", "/oc/api/session/%s" % sid, token=token)
            adb(serial, "shell", "am", "start", "-n", PKG + "/.MainActivity")

    adb(serial, "forward", "--remove", "tcp:%d" % args.port)
    print("\nИТОГ:", "ВСЁ ХОРОШО" if ok else "ЕСТЬ ПРОБЛЕМЫ")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
