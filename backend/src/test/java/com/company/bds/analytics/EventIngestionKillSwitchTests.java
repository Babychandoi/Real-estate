package com.company.bds.analytics;

import com.company.bds.analytics.api.EventIngestionController;
import com.company.bds.analytics.application.EventIngestionService;
import com.company.bds.analytics.application.port.out.AnalyticsEventRepository;
import com.company.bds.analytics.domain.AnalyticsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Review 2 M4: with {@code app.analytics.ingestion.enabled=false} (the application default) nothing is parsed or stored. */
class EventIngestionKillSwitchTests {
    private final AtomicInteger writes = new AtomicInteger();
    private final AnalyticsEventRepository repository = new AnalyticsEventRepository() {
        @Override public int insertWebEvents(List<AnalyticsEvent> events) { writes.addAndGet(events.size()); return events.size(); }
        @Override public boolean insertServerEvent(AnalyticsEvent event) { writes.incrementAndGet(); return true; }
        @Override public java.util.Map<String, java.util.Set<com.company.bds.analytics.domain.DeviceFlag>> deviceFlags(
                java.util.Collection<String> anonymousIds) { return java.util.Map.of(); }
        @Override public boolean flagDevice(String anonymousId, com.company.bds.analytics.domain.DeviceFlag flag, String reason) {
            writes.incrementAndGet();
            return true;
        }
    };

    private MockMvc mvc(boolean enabled) {
        return MockMvcBuilders.standaloneSetup(new EventIngestionController(new EventIngestionService(repository, Clock.systemUTC()),
                new ObjectMapper(), enabled)).build();
    }

    @Test
    void disabledIngestionAnswersServiceUnavailableWithoutReadingTheBody() throws Exception {
        String valid = """
                {"consent":"granted","events":[{"eventId":"%s","name":"kyc_required_shown","v":1,"occurredAt":"%s",
                 "properties":{"context":"lead"}}]}
                """.formatted(UUID.randomUUID(), Instant.now());
        for (String body : List.of(valid, "{not json", "x".repeat(70 * 1024))) {
            mvc(false).perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ANALYTICS_INGESTION_DISABLED"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        assertThat(writes).hasValue(0);

        mvc(true).perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).header("User-Agent", EventIngestionTests.BROWSER)
                        .content(valid))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1));
        assertThat(writes).hasValue(1);
    }
}
