package com.company.bds.search.infrastructure.warmup;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LoopbackHttpWarmupTests {
    private final LoopbackHttpWarmup warmup = new LoopbackHttpWarmup(Clock.systemUTC());

    @Test
    void noWebServerSkipsRatherThanGuessingALocalPort() {
        assertThat(warmup.run(0, List.of(), Instant.now().plusSeconds(5), 30)).isEqualTo("no web server");
    }

    @Test
    void warmsActualLoopbackPublicReadsWithGetAndStopsAfterStableRounds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requests = new CopyOnWriteArrayList<>();
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String result = warmup.run(server.getAddress().getPort(), List.of("/api/v2/listings/example"),
                    Instant.now().plusSeconds(5), 30);
            assertThat(result).contains("rounds ok", "0 failed", "warm");
            assertThat(requests).allMatch(request -> request.startsWith("GET /api/v2/"));
            assertThat(requests).contains("GET /api/v2/listings/search", "GET /api/v2/listings/map", "GET /api/v2/listings/example");
            assertThat(requests.size()).isBetween(66, 180);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void stalledRequestsAreBoundedByTheSharedDeadline() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        Instant start = Instant.now();
        try {
            String result = warmup.run(server.getAddress().getPort(), List.of(), start.plusMillis(200), 30);
            assertThat(result).contains("deadline").doesNotContain("warm");
            assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(2));
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void redirectsAndFailedRoutesDoNotClaimAWarmServerOrVisitAnotherDestination() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/redirect-target", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/redirect-target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            assertThat(warmup.run(server.getAddress().getPort(), List.of(), Instant.now().plusSeconds(5), 1))
                    .contains("0 rounds ok", "1 failed", "bounded");
            assertThat(redirected).hasValue(0);
        } finally {
            server.stop(0);
        }
    }
}
