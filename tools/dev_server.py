#!/usr/bin/env python3
"""Отладочный сервер для проверки page.html + bridge.js в браузере на ПК.
Не входит в APK. Повторяет /oc-прокси и /android-* локального сервера Android."""
import base64, http.client, http.server, json, os, socketserver, urllib.parse, urllib.request
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
APP = BASE / "assets" / "app"
DEMO = BASE / "demo"
UPLOADS = DEMO / "uploads"
OC = ("127.0.0.1", 4097)
PW = os.environ.get("OC_DEV_PASSWORD", "devpass")
AUTH = "Basic " + base64.b64encode(("opencode:" + PW).encode()).decode()
DEMO.mkdir(exist_ok=True)
UPLOADS.mkdir(exist_ok=True)


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("HTTP", fmt % args)

    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        if isinstance(body, (dict, list)):
            body = json.dumps(body, ensure_ascii=False).encode()
        elif isinstance(body, str):
            body = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        p = u.path
        q = urllib.parse.parse_qs(u.query)
        if p in ("/", "/index.html"):
            return self._file(APP / "page.html", "text/html; charset=utf-8")
        if p == "/ping":
            return self._send(200, {"app": "opencode-remote", "ver": 0, "online": True, "name": "PC"})
        if p.startswith("/oc/"):
            return self._proxy("GET", p[3:] + ("?" + u.query if u.query else ""), None)
        if p == "/bridge.js" or (p.startswith("/app/")):
            name = p[7:] if p.startswith("/app/") else "bridge.js"
            return self._file(APP / name, "application/javascript; charset=utf-8")
        if p == "/android/home":
            return self._send(200, {"ok": True, "path": str(DEMO), "allFiles": True})
        if p == "/android/config":
            return self._send(200, {"workdir": str(DEMO), "home": str(Path.home()), "allFiles": True,
                                    "notify": True, "sound": False, "vib": True})
        if p == "/android/fs":
            path = q.get("path", [""])[0]
            return self._send(200, self._fs(path))
        if p == "/android/mkdir":
            parent = q.get("parent", [""])[0]
            name = q.get("name", [""])[0]
            try:
                d = Path(parent) / name
                d.mkdir(parents=True, exist_ok=True)
                return self._send(200, {"ok": True, "path": str(d)})
            except Exception as e:
                return self._send(200, {"ok": False, "error": str(e)})
        if p.startswith("/files/"):
            return self._file(Path(urllib.parse.unquote(p[7:])), "application/octet-stream")
        return self._send(404, "not found")

    def do_POST(self):
        u = urllib.parse.urlparse(self.path)
        p = u.path
        n = int(self.headers.get("Content-Length", "0") or 0)
        body = self.rfile.read(n) if n else b""
        if p.startswith("/oc/"):
            return self._proxy("POST", p[3:] + ("?" + u.query if u.query else ""), body)
        if p in ("/upload", "/upload_raw"):
            name = "upload-" + str(len(list(UPLOADS.glob('*'))) + 1) + ".bin"
            ct = self.headers.get("Content-Type", "")
            if "multipart/form-data" in ct:
                boundary = ct.split("boundary=")[-1].strip().strip('"').encode()
                for part in body.split(b"--" + boundary):
                    if b"filename=" in part:
                        head, _, data = part.partition(b"\r\n\r\n")
                        fn = head.split(b"filename=")[1].split(b"\r\n")[0].split(b";")[0].strip(b'"').decode(errors="ignore")
                        name = os.path.basename(fn) or name
                        (UPLOADS / name).write_bytes(data.rstrip(b"\r\n-"))
                        return self._send(200, {"ok": True, "files": [{"path": str(UPLOADS / name), "name": name}]})
            (UPLOADS / name).write_bytes(body)
            return self._send(200, {"ok": True, "files": [{"path": str(UPLOADS / name), "name": name}]})
        return self._send(404, "not found")

    def _proxy(self, method, path, body):
        try:
            c = http.client.HTTPConnection(*OC, timeout=600)
            headers = {"Authorization": AUTH, "Accept-Encoding": "identity"}
            if self.headers.get("Content-Type"):
                headers["Content-Type"] = self.headers["Content-Type"]
            if self.headers.get("Accept"):
                headers["Accept"] = self.headers["Accept"]
            c.request(method, path, body=body, headers=headers)
            r = c.getresponse()
            ctype = r.getheader("Content-Type", "application/json; charset=utf-8")
            if "text/event-stream" in ctype:
                self.send_response(r.status)
                self.send_header("Content-Type", "text/event-stream; charset=utf-8")
                self.send_header("Cache-Control", "no-cache")
                self.send_header("Connection", "close")
                self.end_headers()
                while True:
                    data = r.read(1)
                    if not data:
                        break
                    self.wfile.write(data)
                    self.wfile.flush()
                return
            data = r.read()
            self.send_response(r.status)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(data)
        except Exception as e:
            self._send(502, {"message": str(e)})

    def _file(self, path, ctype):
        path = Path(path)
        if not path.is_file():
            return self._send(404, "not found")
        data = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _fs(self, path):
        if not path or path == "::drives":
            roots = [{"name": "Демо", "path": str(DEMO), "dir": True, "size": ""},
                     {"name": "Диск C", "path": "C:\\", "dir": True, "size": ""}]
            return {"ok": True, "drives": True, "path": "::drives", "entries": roots,
                    "shortcuts": [{"name": "Демо", "path": str(DEMO)}]}
        p = Path(path)
        entries = []
        try:
            for f in sorted(p.iterdir(), key=lambda x: (not x.is_dir(), x.name.lower())):
                if f.name.startswith("."):
                    continue
                try:
                    size = f.stat().st_size
                except Exception:
                    size = 0
                entries.append({"name": f.name, "path": str(f), "dir": f.is_dir(), "size": "" if f.is_dir() else str(size),
                                "img": f.suffix.lower() in (".png", ".jpg", ".jpeg", ".webp", ".gif")})
        except Exception as e:
            return {"ok": False, "error": str(e), "path": path, "entries": [], "shortcuts": []}
        return {"ok": True, "drives": False, "path": str(p), "parent": str(p.parent),
                "entries": entries,
                "shortcuts": [{"name": "Демо", "path": str(DEMO)},
                              {"name": "Загрузки", "path": str(Path.home() / "Downloads")}]}


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    print("dev: http://127.0.0.1:8770/  → opencode", OC)
    Server(("127.0.0.1", 8770), Handler).serve_forever()
