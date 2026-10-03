package com.company.bds.search.infrastructure.warmup;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Read-only loopback requests warm the real MVC/security/Jackson paths before readiness, within the shared deadline. */
final class LoopbackHttpWarmup {
    private static final List<String> PUBLIC_READS = List.of(
            "/api/v2/listings/search?purpose=SALE&size=24",
            "/api/v2/listings/search?purpose=RENT&size=24",
            "/api/v2/listings/search?q=can%20ho&size=24",
            "/api/v2/listings/map?purpose=SALE&zoom=11&bbox=105.70,20.95,105.90,21.10",
            "/api/v2/listings/map?purpose=SALE&zoom=15&bbox=105.785,21.028,105.795,21.034");
    private final Clock clock;

    LoopbackHttpWarmup(Clock clock) {
        this.clock = clock;
    }

    String run(int port, List<String> additionalReads, Instant deadline, int maxRounds) {
        if (port <= 0) return "no web server"; // MockMvc / non-web contexts must never contact an unrelated local server.
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        List<String> paths = new ArrayList<>(PUBLIC_READS);
        paths.addAll(additionalReads);
        int completed = 0;
        int failed = 0;
        int consecutiveFast = 0;
        for (int round = 0; round < maxRounds && clock.instant().isBefore(deadline); round++) {
            Instant begin = clock.instant();
            boolean success = true;
            for (String path : paths) {
                Duration remaining = Duration.between(clock.instant(), deadline);
                if (remaining.isZero() || remaining.isNegative()) return result(completed, failed + (success ? 0 : 1), consecutiveFast, "deadline");
                Duration timeout = remaining.compareTo(Duration.ofSeconds(2)) < 0 ? remaining : Duration.ofSeconds(2);
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(timeout).GET().build();
                // JDK 17 request.timeout stops at response headers; bound the complete body subscription too.
                var response = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
                try {
                    if (response.get(timeout.toNanos(), TimeUnit.NANOSECONDS).statusCode() != 200) success = false;
                } catch (ExecutionException ex) {
                    success = false;
                } catch (TimeoutException ex) {
                    response.cancel(true);
                    success = false;
                } catch (InterruptedException ex) {
                    response.cancel(true);
                    Thread.currentThread().interrupt();
                    return result(completed, failed + 1, 0, "interrupted");
                }
            }
            if (success) completed++;
            else failed++;
            consecutiveFast = success && Duration.between(begin, clock.instant()).toMillis() < 400 ? consecutiveFast + 1 : 0;
            if (consecutiveFast >= 5 && round >= 10) return result(completed, failed, consecutiveFast, "warm");
        }
        return result(completed, failed, consecutiveFast, "bounded");
    }

    private static String result(int completed, int failed, int fast, String outcome) {
        return completed + " rounds ok, " + failed + " failed, " + fast + " fast, " + outcome;
    }
}
