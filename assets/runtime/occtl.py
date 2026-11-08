#!/usr/bin/env python3
"""Клиент к DeviceService внутри приложения OpenCode.

Примеры:
  occtl settings get|put|delete|list system|secure|global KEY [VALUE]
  occtl pkg list [-3]
  occtl pkg path org.telegram.messenger
  occtl app uninstall ai.android.claw
  occtl app details ai.android.claw
  occtl toast "Готово"
  occtl notify "OpenCode" "Текст"
  occtl info
  occtl sh pm list packages          # shell-команда через Shizuku (полный доступ)
  occtl sh-ready                     # проверка полного доступа (exit 0 = есть)
"""
import json
import os
import shlex
import socket
import sys

PRIMARY = "/data/user/0/ru.opencode.android/files/.occtl"


def endpoint():
    paths = [PRIMARY]
    here = os.path.abspath(__file__)
    # .../files/usr/bin/occtl.py -> .../files/.occtl
    paths.append(os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(here))), ".occtl"))
    last = None
    for path in paths:
        try:
            with open(path, "r", encoding="utf-8") as fh:
                port, token = fh.read().strip().split(":", 1)
            return int(port), token
        except Exception as exc:  # noqa: BLE001
            last = exc
    raise SystemExit("occtl: не найден endpoint (%s)" % last)


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    port, token = endpoint()
    if args[0] == "sh":
        payload = {
            "token": token,
            "cmd": "sh",
            "args": [shlex.join(args[1:]) if len(args) > 1 else ""],
            "cwd": os.getcwd(),
        }
    else:
        payload = {"token": token, "cmd": args[0], "args": args[1:], "cwd": os.getcwd()}
    req = json.dumps(payload, ensure_ascii=False) + "\n"
    with socket.create_connection(("127.0.0.1", port), timeout=120) as conn:
        conn.sendall(req.encode("utf-8"))
        data = b""
        while True:
            chunk = conn.recv(65536)
            if not chunk:
                break
            data += chunk
            if b"\n" in data:
                break
    resp = json.loads(data.decode("utf-8", "replace").strip() or "{}")
    if resp.get("ok"):
        out = resp.get("out") or ""
        if out:
            sys.stdout.write(out if out.endswith("\n") else out + "\n")
        return int(resp.get("code") or 0)
    sys.stderr.write((resp.get("error") or "occtl: error") + "\n")
    return 1


if __name__ == "__main__":
    sys.exit(main())
