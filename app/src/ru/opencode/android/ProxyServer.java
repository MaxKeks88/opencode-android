package ru.opencode.android;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/* JADX INFO: loaded from: C:\Users\WorkPC\AppData\Local\Temp\opencode\apkdex\classes.dex */
public class ProxyServer {
    private static Thread acceptor;
    public static volatile int port = 0;
    private static volatile boolean running = false;
    private static ServerSocket server;

    public static synchronized void start(Context context) {
        if (!running || server == null || server.isClosed()) {
            try {
                ServerSocket serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
                server = serverSocket;
                port = serverSocket.getLocalPort();
                running = true;
                acceptor = new Thread(new Runnable() { // from class: ru.opencode.android.ProxyServer.1
                    @Override // java.lang.Runnable
                    public void run() {
                        ProxyServer.acceptLoop();
                    }
                }, "oc-proxy");
                acceptor.setDaemon(true);
                acceptor.start();
                App.log("DNS-прокси: 127.0.0.1:" + port);
                writeProxyFile(port);
            } catch (Throwable th) {
                App.log("proxy start: " + String.valueOf(th));
            }
        }
    }

    public static synchronized void stop() {
        running = false;
        try {
            if (server != null) {
                server.close();
            }
        } catch (Throwable th) {
        }
        server = null;
        port = 0;
        try {
            new File(App.root, ".proxy").delete();
        } catch (Throwable th2) {
        }
    }

    private static void writeProxyFile(int i) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(App.root, ".proxy"));
            fileOutputStream.write(("http://127.0.0.1:" + i).getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
        } catch (Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void acceptLoop() {
        while (running) {
            try {
                final Socket socketAccept = server.accept();
                Thread thread = new Thread(new Runnable() { // from class: ru.opencode.android.ProxyServer.2
                    @Override // java.lang.Runnable
                    public void run() {
                        ProxyServer.handle(socketAccept);
                    }
                }, "oc-proxy-conn");
                thread.setDaemon(true);
                thread.start();
            } catch (Throwable th) {
                if (!running) {
                    return;
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code duplicated, block: B:133:0x01eb A[EXC_TOP_SPLITTER, SYNTHETIC] */
    public static void handle(Socket socket) {
        Socket upstream = null;
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(30000);
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int last4 = 0;
            int b;
            while ((b = in.read()) != -1) {
                head.write(b);
                last4 = (last4 << 8) | (b & 0xff);
                if (head.size() >= 4 && last4 == 0x0d0a0d0a) {
                    break;
                }
                if (head.size() > 65536) {
                    throw new IOException("head too large");
                }
            }
            if (head.size() == 0) {
                socket.close();
                return;
            }
            String str = new String(head.toByteArray(), StandardCharsets.ISO_8859_1);
            int indexOf = str.indexOf("\r\n");
            String[] split = (indexOf >= 0 ? str.substring(0, indexOf) : str).split(" ");
            if (split.length < 2) {
                throw new IOException("bad request line");
            }
            String method = split[0];
            String target = split[1];
            if (!"CONNECT".equalsIgnoreCase(method)) {
                URI create = URI.create(target);
                String host = create.getHost();
                if (host == null) {
                    throw new IOException("no host in " + target);
                }
                int port2 = create.getPort() > 0 ? create.getPort() : 80;
                String rawPath = create.getRawPath();
                if (rawPath == null || rawPath.isEmpty()) {
                    rawPath = "/";
                }
                if (create.getRawQuery() != null) {
                    rawPath = rawPath + "?" + create.getRawQuery();
                }
                upstream = connectUpstream(host, port2);
                socket.setSoTimeout(0);
                upstream.setSoTimeout(0);
                String rest = indexOf >= 0 ? str.substring(indexOf + 2) : "";
                StringBuilder sb = new StringBuilder();
                sb.append(method).append(' ').append(rawPath).append(" HTTP/1.1\r\n");
                for (String header : rest.split("\r\n")) {
                    if (!header.isEmpty() && !header.regionMatches(true, 0, "Proxy-Connection:", 0, 17) && !header.regionMatches(true, 0, "Connection:", 0, 11)) {
                        sb.append(header).append("\r\n");
                    }
                }
                sb.append("Connection: close\r\n\r\n");
                OutputStream outputStream = upstream.getOutputStream();
                outputStream.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
                outputStream.flush();
                tunnel(socket, upstream);
                return;
            }
            int lastIndexOf = target.lastIndexOf(58);
            String host;
            int port2;
            if (lastIndexOf > 0) {
                host = target.substring(0, lastIndexOf);
                port2 = Integer.parseInt(target.substring(lastIndexOf + 1));
            } else {
                host = target;
                port2 = 443;
            }
            upstream = connectUpstream(host, port2);
            socket.setSoTimeout(0);
            upstream.setSoTimeout(0);
            OutputStream outputStream2 = socket.getOutputStream();
            outputStream2.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            outputStream2.flush();
            tunnel(socket, upstream);
        } catch (Throwable th) {
            App.log("proxy conn: " + String.valueOf(th));
        } finally {
            if (upstream != null) {
                try {
                    upstream.close();
                } catch (Throwable th2) {
                }
            }
            try {
                socket.close();
            } catch (Throwable th3) {
            }
        }
    }

    private static Socket connectUpstream(String host, int port) throws IOException {
        Socket s = new Socket();
        try {
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(host, port), 15000);
            return s;
        } catch (IOException e) {
            try {
                s.close();
            } catch (Throwable th) {
            }
            throw e;
        }
    }

    private static void tunnel(final Socket socket, final Socket socket2) {
        Thread thread = new Thread(new Runnable() { // from class: ru.opencode.android.ProxyServer.3
            @Override // java.lang.Runnable
            public void run() {
                ProxyServer.copy(socket, socket2);
            }
        }, "oc-proxy-up");
        thread.setDaemon(true);
        thread.start();
        copy(socket2, socket);
        try {
            thread.join(2000L);
        } catch (InterruptedException e) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void copy(Socket socket, Socket socket2) {
        try {
            try {
                InputStream inputStream = socket.getInputStream();
                OutputStream outputStream = socket2.getOutputStream();
                byte[] bArr = new byte[32768];
                while (true) {
                    int i = inputStream.read(bArr);
                    if (i != -1) {
                        outputStream.write(bArr, 0, i);
                        outputStream.flush();
                    } else {
                        socket2.shutdownOutput();
                        return;
                    }
                }
            } catch (Throwable th) {
                socket2.shutdownOutput();
            }
        } catch (Throwable th2) {
        }
    }
}
