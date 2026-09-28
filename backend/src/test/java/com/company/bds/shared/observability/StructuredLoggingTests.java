package com.company.bds.shared.observability;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F22.2 on the real logging configuration of the application (application.yml, started by the Spring context): each
 * line is one JSON object, carries the request's {@code requestId}/{@code traceId} and never the PII the code logged.
 */
@BdsIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTests {
    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingTests.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;

    @RestController
    static class LoggingController {
        @GetMapping("/s9/log")
        String logSomething() {
            log.info("s9-marker contact nguyen.van.a@example.com phone 0912 345 678 auth Bearer abc.def.ghi password=hunter2");
            try {
                throw new IllegalStateException("s9-failure for tran.b@example.org");
            } catch (IllegalStateException ex) {
                log.warn("s9-marker-exception", ex);
            }
            return "ok";
        }
    }

    @Test
    void everyLineOfARequestIsJsonWithItsRequestIdAndNoPii(CapturedOutput output) throws Exception {
        MockMvc standalone = MockMvcBuilders.standaloneSetup(new LoggingController()).addFilters(new RequestIdFilter()).build();

        MvcResult result = standalone.perform(get("/s9/log").header("X-Request-Id", "s9-req-0001"))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo("s9-req-0001");
        List<JsonNode> lines = output.getOut().lines().filter(line -> line.contains("s9-marker")).map(StructuredLoggingTests::parse).toList();
        assertThat(lines).hasSize(2);
        for (JsonNode line : lines) {
            assertThat(line.path("requestId").asText()).isEqualTo("s9-req-0001");
            assertThat(line.path("traceId").asText()).isEqualTo("s9-req-0001");
            assertThat(line.path("level").asText()).isNotBlank();
            assertThat(line.path("logger_name").asText()).isEqualTo(StructuredLoggingTests.class.getName());
            assertThat(line.path("@timestamp").asText()).isNotBlank();
        }
        assertThat(lines.get(0).path("message").asText())
                .isEqualTo("s9-marker contact [EMAIL] phone [PHONE] auth Bearer [REDACTED] password=[REDACTED]");
        assertThat(lines.get(1).path("stack_trace").asText()).contains("s9-failure for [EMAIL]");
        assertThat(output.getAll()).doesNotContain("nguyen.van.a@example.com", "0912 345 678", "abc.def.ghi", "hunter2",
                "tran.b@example.org");
        // the MDC is cleared when the request ends: nothing leaks into the next request on this thread
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void theRealStackEchoesTheRequestIdAndUsesTheTraceparentTraceId() throws Exception {
        // an unsafe inbound id (header injection attempt) is replaced by a generated one
        MvcResult generated = mvc.perform(get("/api/v1/me/saved-listings").header("X-Request-Id", "bad id\r\nX-Injected: 1"))
                .andExpect(status().isUnauthorized()).andReturn();
        String requestId = generated.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(JSON.readTree(generated.getResponse().getContentAsString()).path("traceId").asText()).isEqualTo(requestId);

        // Nginx's id is kept; a W3C traceparent supplies the trace id
        MvcResult traced = mvc.perform(get("/api/v1/me/saved-listings")
                        .header("X-Request-Id", "nginx0123456789abcdef")
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(traced.getResponse().getHeader("X-Request-Id")).isEqualTo("nginx0123456789abcdef");
        assertThat(JSON.readTree(traced.getResponse().getContentAsString()).path("traceId").asText())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    private static JsonNode parse(String line) {
        try {
            return Objects.requireNonNull(JSON.readTree(line));
        } catch (Exception ex) {
            throw new AssertionError("log line is not JSON: " + line, ex);
        }
    }
}
