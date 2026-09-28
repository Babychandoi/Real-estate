package com.company.bds.notification;

import com.company.bds.BdsApplication;
import com.company.bds.notification.infrastructure.NotificationSseHub;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit F12: two application instances on the same PostgreSQL and Redis. This test context is instance A (writes);
 * instance B is a second, real Spring Boot application with an HTTP port, and the SSE clients connect to B only.
 * Proves cross-instance fan-out through Redis Pub/Sub, once-only delivery, nothing for a rolled-back write, replay
 * with {@code Last-Event-ID} (including a notification whose transaction committed after a newer one was delivered),
 * the per-user stream cap, heartbeats and removal of dead streams.
 */
@BdsIntegrationTest
class NotificationFanoutTests {
    private static ConfigurableApplicationContext nodeB;
    private static int portB;

    @Autowired Environment environment;
    @Autowired RealtimeNotificationService notifications;
    @Autowired TestData data;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper json;

    @BeforeEach
    void startSecondInstance() {
        if (nodeB != null) return;
        // Command-line arguments: the highest-precedence property source (application.yml would override defaults).
        List<String> args = new java.util.ArrayList<>();
        for (String key : List.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                "spring.data.redis.database")) {
            args.add("--" + key + "=" + environment.getProperty(key));
        }
        args.add("--server.port=0");
        args.add("--spring.flyway.enabled=false");
        nodeB = new SpringApplicationBuilder(BdsApplication.class).profiles("test").run(args.toArray(String[]::new));
        portB = ((WebServerApplicationContext) nodeB).getWebServer().getPort();
    }

    @AfterAll
    static void stopSecondInstance() {
        if (nodeB != null) nodeB.close();
        nodeB = null;
    }

    private String streamUrl() {
        return "http://127.0.0.1:" + portB + "/api/v1/notifications/stream";
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private UUID notifyOnA(UUID userId, String title) {
        return tx().execute(status -> notifications.notify(new NotificationRequest(userId, "LISTING_TEST_EVENT", title,
                "Nội dung " + title, "/my-listings", null, false))).orElseThrow();
    }

    @Test
    void aNotificationWrittenOnInstanceAReachesAStreamOnInstanceBExactlyOnce() throws Exception {
        TestData.TestUser user = data.user().create();
        String token = data.sessionFor(user.id());
        try (SseTestClient client = new SseTestClient(streamUrl(), token, null)) {
            assertThat(client.status()).isEqualTo(200);
            assertThat(client.next("ready", Duration.ofSeconds(10))).as("stream ready on B").isNotNull();

            UUID id = notifyOnA(user.id(), "Liên node A→B");
            SseTestClient.Frame frame = client.next("notification", Duration.ofSeconds(10));
            assertThat(frame).as("frame delivered through Redis to instance B").isNotNull();
            JsonNode body = json.readTree(frame.data());
            assertThat(body.path("id").asText()).isEqualTo(id.toString());
            assertThat(frame.id()).isEqualTo(body.path("seq").asText());
            assertThat(body.path("title").asText()).isEqualTo("Liên node A→B");
            assertThat(body.path("link").asText()).isEqualTo("/my-listings");
            assertThat(body.path("category").asText()).isEqualTo("LISTINGS");
            assertThat(client.next("notification", Duration.ofMillis(1500))).as("no duplicate frame").isNull();

            // A rolled-back write publishes nothing.
            tx().executeWithoutResult(status -> {
                notifications.notify(NotificationRequest.of(user.id(), "LISTING_TEST_EVENT", "Không commit", "x"));
                status.setRollbackOnly();
            });
            assertThat(client.next("notification", Duration.ofMillis(1500))).isNull();
        }
    }

    @Test
    void reconnectingWithLastEventIdReplaysWhatWasMissedIncludingALateCommit() throws Exception {
        TestData.TestUser user = data.user().create();
        String token = data.sessionFor(user.id());
        notifyOnA(user.id(), "Trước khi mất kết nối");
        long seen;
        try (SseTestClient first = new SseTestClient(streamUrl(), token, null)) {
            assertThat(first.next("ready", Duration.ofSeconds(10))).isNotNull();
            notifyOnA(user.id(), "Đã nhận trực tiếp");
            seen = Long.parseLong(first.next("notification", Duration.ofSeconds(10)).id());
        }
        // While disconnected: one ordinary notification, and one whose transaction took a LOWER sequence number than a
        // notification the client already received live, but committed later.
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        long liveSeq;
        Future<UUID> slow;
        try (SseTestClient live = new SseTestClient(streamUrl(), token, Long.toString(seen))) {
            assertThat(live.next("ready", Duration.ofSeconds(10))).isNotNull();
            slow = pool.submit(() -> tx().execute(status -> {
                UUID id = notifications.notify(NotificationRequest.of(user.id(), "LISTING_TEST_EVENT", "Commit muộn", "x")).orElseThrow();
                inserted.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return id;
            }));
            assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
            notifyOnA(user.id(), "Commit sớm");
            liveSeq = Long.parseLong(live.next("notification", Duration.ofSeconds(10)).id());
        }
        release.countDown();
        long lateSeq = jdbcSeq(slow.get(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(lateSeq).as("the late commit took the lower sequence number").isLessThan(liveSeq);
        notifyOnA(user.id(), "Trong lúc mất kết nối");

        try (SseTestClient again = new SseTestClient(streamUrl(), token, Long.toString(liveSeq))) {
            List<SseTestClient.Frame> replayed = again.until("notification", "ready", Duration.ofSeconds(10));
            List<String> titles = replayed.stream().map(f -> title(f.data())).toList();
            assertThat(titles).contains("Commit muộn", "Trong lúc mất kết nối");
            // Frames carry their seq as id so the client can drop the ones it already has (look-back window).
            assertThat(replayed).allSatisfy(f -> assertThat(f.id()).isEqualTo(Long.toString(json.readTree(f.data()).path("seq").asLong())));
        }
    }

    @Test
    void streamsAreCappedPerUserHeartbeatsFlowAndDeadStreamsAreRemoved() throws Exception {
        TestData.TestUser user = data.user().create();
        String token = data.sessionFor(user.id());
        NotificationSseHub hubB = nodeB.getBean(NotificationSseHub.class);
        java.util.ArrayList<SseTestClient> clients = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 7; i++) {
                SseTestClient client = new SseTestClient(streamUrl(), token, null);
                assertThat(client.next("ready", Duration.ofSeconds(10))).isNotNull();
                clients.add(client);
            }
            assertThat(hubB.openStreams(user.id())).isEqualTo(5);
            assertThat(clients.get(0).next("__eof", Duration.ofSeconds(5))).as("oldest stream closed by the cap").isNotNull();

            hubB.heartbeat();
            SseTestClient newest = clients.get(clients.size() - 1);
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (newest.comments().stream().noneMatch("hb"::equals) && System.nanoTime() < deadline) Thread.sleep(50);
            assertThat(newest.comments()).contains("hb");

            for (int i = 2; i < clients.size(); i++) clients.get(i).close();
            deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (hubB.openStreams(user.id()) > 0 && System.nanoTime() < deadline) {
                hubB.heartbeat();
                Thread.sleep(100);
            }
            assertThat(hubB.openStreams(user.id())).as("disconnected clients are forgotten").isZero();
        } finally {
            clients.forEach(SseTestClient::close);
        }
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private long jdbcSeq(UUID id) {
        Long seq = jdbc.queryForObject("SELECT seq FROM user_notifications WHERE id = ?", Long.class, id);
        return seq == null ? -1 : seq;
    }

    private String title(String data) {
        try {
            return json.readTree(data).path("title").asText();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
