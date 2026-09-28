package com.company.bds.notification;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A real HTTP SSE client for tests: parses frames ({@code id}, {@code event}, {@code data}) as they arrive. */
final class SseTestClient implements AutoCloseable {
    record Frame(String id, String event, String data) {}

    private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
    private final CompletableFuture<HttpResponse<InputStream>> response;
    private final List<String> comments = new ArrayList<>();
    private volatile InputStream body;
    private volatile Thread reader;

    SseTestClient(String url, String bearer, String lastEventId) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2))
                .header("Accept", "text/event-stream").header("Authorization", "Bearer " + bearer).GET();
        if (lastEventId != null) request.header("Last-Event-ID", lastEventId);
        this.response = HttpClient.newHttpClient().sendAsync(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        this.response.thenAccept(r -> {
            body = r.body();
            reader = new Thread(this::read, "sse-test-client");
            reader.setDaemon(true);
            reader.start();
        });
    }

    int status() throws Exception {
        return response.get(10, TimeUnit.SECONDS).statusCode();
    }

    /** Next frame of the given event name, skipping others; null after the timeout. */
    Frame next(String event, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long left = deadline - System.nanoTime();
            if (left <= 0) return null;
            Frame frame = frames.poll(left, TimeUnit.NANOSECONDS);
            if (frame == null) return null;
            if (event.equals(frame.event())) return frame;
        }
    }

    /** Every frame of the event until {@code until} arrives (exclusive) or the timeout passes. */
    List<Frame> until(String event, String until, Duration timeout) throws InterruptedException {
        List<Frame> out = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long left = deadline - System.nanoTime();
            if (left <= 0) return out;
            Frame frame = frames.poll(left, TimeUnit.NANOSECONDS);
            if (frame == null) return out;
            if (until.equals(frame.event())) return out;
            if (event.equals(frame.event())) out.add(frame);
        }
    }

    synchronized List<String> comments() {
        return List.copyOf(comments);
    }

    private void read() {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String id = null;
            String event = "message";
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() > 0 || !"message".equals(event)) frames.add(new Frame(id, event, data.toString()));
                    id = null;
                    event = "message";
                    data.setLength(0);
                } else if (line.startsWith(":")) {
                    synchronized (this) { comments.add(line.substring(1)); }
                } else if (line.startsWith("id:")) {
                    id = line.substring(3).trim();
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) data.append('\n');
                    data.append(line.substring(5).trim());
                }
            }
            frames.add(new Frame(null, "__eof", ""));
        } catch (Exception ignored) {
            frames.add(new Frame(null, "__eof", ""));
        }
    }

    @Override
    public void close() {
        response.cancel(true);
        try {
            if (body != null) body.close();
        } catch (Exception ignored) {
            // closing an aborted stream
        }
        if (reader != null) reader.interrupt();
    }
}
