package com.company.bds.search;

import com.company.bds.search.application.SearchCircuitBreaker;
import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Elasticsearch that accepts connections but never answers (the worst failure: a hang). Every search must still answer
 * from the database within the 800 ms budget plus the database time, mark itself degraded, and after enough failures
 * the breaker stops calling the engine at all (F06.3).
 */
@BdsIntegrationTest(properties = {"app.search.elasticsearch.enabled=true", "app.search.bootstrap-on-startup=false",
        "app.search.cache.first-page=false"})
class SearchEngineFallbackTests {
    private static final ServerSocket BLACKHOLE;
    private static final List<Socket> ACCEPTED = new CopyOnWriteArrayList<>();

    static {
        try {
            BLACKHOLE = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        Thread acceptor = new Thread(() -> {
            while (!BLACKHOLE.isClosed()) {
                try {
                    ACCEPTED.add(BLACKHOLE.accept()); // keep the connection open, never write a byte
                } catch (IOException ignored) {
                    return;
                }
            }
        }, "s2-es-blackhole");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @DynamicPropertySource
    static void elasticsearch(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://127.0.0.1:" + BLACKHOLE.getLocalPort());
        registry.add("app.search.index-name", () -> "s2-blackhole");
    }

    @AfterAll
    static void close() throws IOException {
        for (Socket socket : ACCEPTED) socket.close();
        BLACKHOLE.close();
    }

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired SearchIndexSettings settings;
    @Autowired SearchCircuitBreaker breaker;
    @Autowired MeterRegistry meters;

    @Test
    void aHangingEngineFallsBackToTheDatabaseWithinTheBudgetAndOpensTheBreaker() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = data.user().role("BROKER").create();
        for (int i = 0; i < 3; i++) data.listing(seller.id()).title("Căn hộ " + token + " " + i).create();
        settings.markReady(true); // pretend the alias exists: the engine is then called and times out
        breaker.reset();

        long started = System.nanoTime();
        JsonNode page = json.readTree(mvc.perform(get("/api/v2/listings/search").param("q", token)).andReturn()
                .getResponse().getContentAsString());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(page.path("engine").asText()).isEqualTo("database");
        assertThat(page.path("degraded").asBoolean()).isTrue();
        assertThat(page.path("notices").toString()).contains("SEARCH_ENGINE_UNAVAILABLE", "RELEVANCE_APPROXIMATE");
        assertThat(page.path("items")).hasSize(3);
        assertThat(elapsedMs).as("800 ms engine budget + database").isLessThan(2_500);

        for (int i = 0; i < 4; i++) mvc.perform(get("/api/v2/listings/search").param("q", token)).andReturn();
        assertThat(breaker.state()).isEqualTo(SearchCircuitBreaker.State.OPEN);
        long openStarted = System.nanoTime();
        JsonNode whileOpen = json.readTree(mvc.perform(get("/api/v2/listings/search").param("q", token)).andReturn()
                .getResponse().getContentAsString());
        assertThat((System.nanoTime() - openStarted) / 1_000_000).as("no engine call while open").isLessThan(700);
        assertThat(whileOpen.path("engine").asText()).isEqualTo("database");
        assertThat(whileOpen.path("degraded").asBoolean()).isTrue();
        assertThat(meters.get("bds.search.requests").tag("engine", "database").tag("outcome", "fallback").counter().count())
                .isGreaterThanOrEqualTo(5);
        assertThat(meters.get("bds.search.breaker.state").gauge().value()).isEqualTo(1.0);
        settings.markReady(false);
    }
}
