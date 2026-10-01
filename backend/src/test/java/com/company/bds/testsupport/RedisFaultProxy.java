package com.company.bds.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCP proxy between the application and the shared test Redis, so a test can take "its" Redis away without touching
 * the shared server: {@link #stop()} drops every connection and refuses new ones (Redis stopped or restarting),
 * {@link #hang()} keeps connections open but delivers nothing until {@link #forward()} (Redis frozen or a network
 * partition: commands time out instead of being refused). Bytes are only delayed, never dropped, so the Redis protocol
 * stays in sync when forwarding resumes.
 */
public final class RedisFaultProxy implements AutoCloseable {
    private enum Mode { FORWARD, HANG, STOP }

    private final InetSocketAddress target;
    private final ServerSocket server;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final Object gate = new Object();
    private volatile Mode mode = Mode.FORWARD;
    private volatile boolean closed;

    private RedisFaultProxy(InetSocketAddress target) throws IOException {
        this.target = target;
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "redis-fault-proxy-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** A proxy in front of the shared test Redis (BDS_TEST_REDIS_HOST/PORT). */
    public static RedisFaultProxy toSharedTestRedis() {
        String host = BdsTestEnvironment.optional("BDS_TEST_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(BdsTestEnvironment.optional("BDS_TEST_REDIS_PORT", "56379"));
        try {
            return new RedisFaultProxy(new InetSocketAddress(host, port));
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot open the Redis fault proxy", ex);
        }
    }

    public int port() { return server.getLocalPort(); }

    public void forward() {
        synchronized (gate) {
            mode = Mode.FORWARD;
            gate.notifyAll();
        }
    }

    public void hang() { mode = Mode.HANG; }

    public void stop() {
        mode = Mode.STOP;
        for (Socket socket : sockets) closeQuietly(socket);
        sockets.clear();
    }

    @Override
    public void close() {
        closed = true;
        stop();
        forward();
        closeQuietly(server);
    }

    private void acceptLoop() {
        while (!closed) {
            Socket client;
            try {
                client = server.accept();
            } catch (IOException ex) {
                return;
            }
            if (mode == Mode.STOP) {
                closeQuietly(client);
                continue;
            }
            try {
                Socket upstream = new Socket();
                upstream.connect(target, 2_000);
                sockets.add(client);
                sockets.add(upstream);
                pump(client, upstream);
                pump(upstream, client);
            } catch (IOException ex) {
                closeQuietly(client);
            }
        }
    }

    private void pump(Socket from, Socket to) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    awaitForwarding();
                    if (mode == Mode.STOP) break;
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (IOException | InterruptedException ignored) {
                // connection dropped
            } finally {
                closeQuietly(from);
                closeQuietly(to);
                sockets.remove(from);
                sockets.remove(to);
            }
        }, "redis-fault-proxy-pump");
        thread.setDaemon(true);
        thread.start();
    }

    private void awaitForwarding() throws InterruptedException {
        synchronized (gate) {
            while (mode == Mode.HANG && !closed) gate.wait();
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
