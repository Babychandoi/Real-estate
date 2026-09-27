package com.company.bds.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only client for the shared Mailpit test inbox ({@code BDS_TEST_MAILPIT_API}). The inbox is shared by every stream,
 * so tests always send to a unique recipient and only search for it; they never delete messages.
 */
public final class MailpitClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;

    public MailpitClient() {
        this.baseUrl = BdsTestEnvironment.require("BDS_TEST_MAILPIT_API").replaceAll("/+$", "");
    }

    public static String uniqueAddress(String purpose) {
        return "s0be-" + purpose + "-" + java.util.UUID.randomUUID().toString().substring(0, 12) + "@example.test";
    }

    /** Subjects of every message whose To header contains the address. */
    public List<String> subjectsTo(String address) {
        List<String> subjects = new ArrayList<>();
        for (JsonNode message : search("to:\"" + address + "\"").path("messages")) {
            subjects.add(message.path("Subject").asText());
        }
        return subjects;
    }

    /** Plain-text body of the newest message sent to the address. */
    public String latestTextTo(String address) {
        JsonNode messages = search("to:\"" + address + "\"").path("messages");
        if (messages.isEmpty()) throw new AssertionError("No message delivered to " + address);
        String id = messages.get(0).path("ID").asText();
        return get("/api/v1/message/" + id).path("Text").asText();
    }

    private JsonNode search(String query) {
        return get("/api/v1/search?limit=50&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    private JsonNode get(String path) {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Mailpit " + response.statusCode() + ": " + response.body());
            return JSON.readTree(response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("Mailpit API unreachable at " + baseUrl + ": " + ex.getMessage(), ex);
        }
    }
}
