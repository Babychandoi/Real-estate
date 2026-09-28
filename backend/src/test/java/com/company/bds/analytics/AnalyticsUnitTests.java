package com.company.bds.analytics;

import com.company.bds.analytics.api.EventIngestionController;
import com.company.bds.analytics.application.AnalyticsDashboardService;
import com.company.bds.analytics.application.EventIngestionService;
import com.company.bds.analytics.application.port.out.AnalyticsDashboardQueries;
import com.company.bds.analytics.application.port.out.AnalyticsEventRepository;
import com.company.bds.analytics.domain.AnalyticsEvent;
import com.company.bds.analytics.domain.DeviceFlag;
import com.company.bds.analytics.domain.InternalNetworks;
import com.company.bds.analytics.domain.Metric;
import com.company.bds.shared.security.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S8 unit tests: internal networks, metric invariants, the dashboard when web collection is off. */
class AnalyticsUnitTests {

    @Test
    void internalNetworksMatchCidrsAndNeverResolveHostNames() {
        InternalNetworks networks = InternalNetworks.parse(" 10.0.0.0/8, 203.0.113.7 ,2001:db8::/32");
        assertThat(networks.contains("10.20.30.40")).isTrue();
        assertThat(networks.contains("11.0.0.1")).isFalse();
        assertThat(networks.contains("203.0.113.7")).isTrue();
        assertThat(networks.contains("203.0.113.8")).isFalse();
        assertThat(networks.contains("2001:db8:1::5")).isTrue();
        assertThat(networks.contains("2001:db9::1")).isFalse();
        assertThat(networks.contains("localhost")).as("host names are never looked up").isFalse();
        assertThat(networks.contains(null)).isFalse();
        assertThat(InternalNetworks.parse("").contains("10.0.0.1")).isFalse();
        assertThatThrownBy(() -> InternalNetworks.parse("office.example.com/24")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InternalNetworks.parse("10.0.0.0/33")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trafficFromAnInternalNetworkIsMarkedInternal() throws Exception {
        List<AnalyticsEvent> stored = new ArrayList<>();
        AnalyticsEventRepository repository = new AnalyticsEventRepository() {
            @Override public int insertWebEvents(List<AnalyticsEvent> events) { stored.addAll(events); return events.size(); }
            @Override public boolean insertServerEvent(AnalyticsEvent event) { return true; }
            @Override public Map<String, Set<DeviceFlag>> deviceFlags(Collection<String> anonymousIds) { return Map.of(); }
            @Override public boolean flagDevice(String anonymousId, DeviceFlag flag, String reason) { return true; }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EventIngestionController(new EventIngestionService(repository, Clock.systemUTC()),
                null, new ObjectMapper(), true, new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED_PROXIES), "198.51.100.0/24")).build();
        for (String ip : List.of("198.51.100.23", "192.0.2.10")) {
            String body = """
                    {"consent":"granted","events":[{"eventId":"%s","name":"kyc_required_shown","v":1,"occurredAt":"%s",
                      "anonymousId":"anon-12345678","sessionId":"sess-12345678","properties":{"context":"lead_form"}}]}
                    """.formatted(UUID.randomUUID(), Instant.now().minusSeconds(1));
            mvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(body).header("User-Agent", EventIngestionTests.BROWSER)
                    .with(request -> { request.setRemoteAddr(ip); return request; })).andExpect(status().isAccepted());
        }
        assertThat(stored).extracting(AnalyticsEvent::internal).containsExactly(true, false);
    }

    @Test
    void metricsNeverPresentAMissingValueAsZero() {
        assertThat(Metric.percent(0, 0).status()).isEqualTo(Metric.Status.NOT_MEASURED);
        assertThat(Metric.percent(0, 0).value()).isNull();
        assertThat(Metric.percent(0, 5).value()).isZero();
        assertThat(Metric.percent(1, 3).value()).isEqualTo(33.3);
        assertThat(Metric.value(0.0512, "score").value()).isEqualTo(0.051);
        assertThatThrownBy(() -> new Metric(Metric.Status.NOT_MEASURED, 0.0, "count", "x", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Metric(Metric.Status.NOT_MEASURED, null, "count", null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withWebCollectionOffWebMetricsAreNotMeasuredButDatabaseMetricsAre() {
        AnalyticsDashboardService service = new AnalyticsDashboardService(zeroQueries(), Clock.systemUTC(), false);
        AnalyticsDashboardService.Dashboard dashboard = service.dashboard(new AnalyticsDashboardService.DashboardRequest(null, null, null, null, null));

        AnalyticsDashboardService.Funnel search = dashboard.funnels().get(0);
        assertThat(search.key()).isEqualTo("search");
        assertThat(search.steps()).allSatisfy(step -> {
            assertThat(step.count().status()).isEqualTo(Metric.Status.NOT_MEASURED);
            assertThat(step.count().reason()).contains("APP_ANALYTICS_INGESTION_ENABLED");
        });
        AnalyticsDashboardService.Funnel lead = dashboard.funnels().stream().filter(f -> f.key().equals("lead")).findFirst().orElseThrow();
        assertThat(lead.steps().get(0).count().status()).isEqualTo(Metric.Status.MEASURED);
        assertThat(lead.steps().get(0).count().value()).isZero();
        assertThat(dashboard.alerts()).extracting(AnalyticsDashboardService.Alert::code).contains("INGESTION_DISABLED");
        assertThat(dashboard.cohorts().status()).isEqualTo(Metric.Status.NOT_MEASURED);
        assertThat(dashboard.window().days()).isEqualTo(AnalyticsDashboardService.DEFAULT_DAYS);
    }

    /** A queries port answering zeros / empty lists / nulls for every call. */
    private static AnalyticsDashboardQueries zeroQueries() {
        return (AnalyticsDashboardQueries) Proxy.newProxyInstance(AnalyticsDashboardQueries.class.getClassLoader(),
                new Class<?>[] {AnalyticsDashboardQueries.class}, (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (List.class.isAssignableFrom(type)) return List.of();
                    if (!type.isRecord()) throw new UnsupportedOperationException(method.getName());
                    RecordComponent[] components = type.getRecordComponents();
                    Object[] values = new Object[components.length];
                    Class<?>[] types = new Class<?>[components.length];
                    for (int i = 0; i < components.length; i++) {
                        types[i] = components[i].getType();
                        if (types[i] == long.class) values[i] = 0L;
                        else if (types[i] == int.class) values[i] = 0;
                        else if (types[i] == double.class) values[i] = 0.0;
                    }
                    return type.getDeclaredConstructor(types).newInstance(values);
                });
    }
}
