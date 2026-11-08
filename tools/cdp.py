#!/usr/bin/env python3
"""Мини-CDP для WebView телефона через adb forward.
  python tools/cdp.py eval "expression"
  python tools/cdp.py shot path.png
  python tools/cdp.py text
"""
import base64
import json
import sys
import urllib.request
import websocket

PORT = 9222


def target():
    data = json.load(urllib.request.urlopen("http://127.0.0.1:%d/json" % PORT, timeout=10))
    pages = [p for p in data if p.get("type") == "page"]
    if not pages:
        raise SystemExit("no page")
    return pages[0]


class CDP:
    def __init__(self):
        t = target()
        self.ws = websocket.create_connection(t["webSocketDebuggerUrl"], timeout=90,
                                              suppress_origin=True)
        self.n = 0

    def call(self, method, params=None):
        self.n += 1
        mid = self.n
        self.ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        while True:
            msg = json.loads(self.ws.recv())
            if msg.get("id") == mid:
                if "error" in msg:
                    raise RuntimeError(msg["error"])
                return msg.get("result")

    def eval(self, expr):
        r = self.call("Runtime.evaluate", {
            "expression": expr,
            "returnByValue": True,
            "awaitPromise": True,
            "userGesture": True,
        })
        return r.get("result", {}).get("value")

    def close(self):
        try:
            self.ws.close()
        except Exception:
            pass


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd = sys.argv[1]
    c = CDP()
    try:
        if cmd == "eval":
            print(json.dumps(c.eval(sys.argv[2]), ensure_ascii=False, indent=2))
        elif cmd == "text":
            print(json.dumps(c.eval("document.body.innerText"), ensure_ascii=False))
        elif cmd == "shot":
            r = c.call("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": False})
            data = base64.b64decode(r["data"])
            open(sys.argv[2], "wb").write(data)
            print("saved", sys.argv[2], len(data))
        else:
            print(__doc__)
    finally:
        c.close()


if __name__ == "__main__":
    main()
