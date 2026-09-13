package com.company.bds.search;

import com.company.bds.iam.application.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1/public/geocoding")
public class GeocodingController {
    private static final String RATE_KEY = "rate:geocoding:nominatim";
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicLong localNextAllowedAt = new AtomicLong();
    private final String providerUrl;
    private final String contact;
    private final ObjectMapper objectMapper;

    public GeocodingController(JdbcTemplate jdbc, ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectMapper objectMapper,
            @Value("${app.geocoding.provider-url:https://photon.komoot.io}") String providerUrl,
            @Value("${app.geocoding.contact:}") String contact) {
        this.jdbc = jdbc;
        this.redis = redisProvider.getIfAvailable();
        this.providerUrl = providerUrl.replaceAll("/+$", "");
        this.contact = contact;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String search(@RequestParam String q) throws Exception {
        String query = q.trim();
        if (query.length() < 3 || query.length() > 250) {
            throw new IllegalArgumentException("Địa chỉ phải dài từ 3 đến 250 ký tự.");
        }
        String hash = AuthService.sha256(query.toLowerCase());
        var cached = jdbc.queryForList("SELECT response_json FROM geocode_cache WHERE query_hash=?", String.class, hash);
        if (!cached.isEmpty()) return cached.get(0);
        if (!claimProviderSlot()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Dịch vụ định vị đang giới hạn tần suất; vui lòng thử lại sau một giây.");
        }
        String userAgent = "NhaDatChuan/1.0" + (contact.isBlank() ? "" : " (" + contact + ")");
        boolean photon = providerUrl.contains("photon.komoot.io");
        String endpoint = photon
                ? providerUrl + "/api?q=" + URLEncoder.encode(query + ", Vietnam", StandardCharsets.UTF_8) + "&limit=5"
                : providerUrl + "/search?format=jsonv2&limit=5&countrycodes=vn&q="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(8)).header("User-Agent", userAgent).GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Khong the ket noi dich vu dinh vi.", exception);
        }
        if (response.statusCode() != 200) throw new IllegalStateException("Dịch vụ định vị đang bận.");
        String body = photon ? normalizePhoton(response.body()) : response.body();
        jdbc.update("INSERT INTO geocode_cache(query_hash,query_text,response_json) VALUES(?,?,?) ON CONFLICT(query_hash) DO NOTHING",
                hash, query, body);
        return body;
    }

    private String normalizePhoton(String body) throws IOException {
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode feature : objectMapper.readTree(body).path("features")) {
            JsonNode coordinates = feature.path("geometry").path("coordinates");
            JsonNode properties = feature.path("properties");
            if (coordinates.size() < 2) continue;
            ObjectNode item = result.addObject();
            String name = properties.path("name").asText("");
            String city = properties.path("city").asText("");
            String state = properties.path("state").asText("");
            String country = properties.path("country").asText("Vietnam");
            item.put("display_name", java.util.stream.Stream.of(name, city, state, country)
                    .filter(value -> !value.isBlank()).distinct()
                    .collect(java.util.stream.Collectors.joining(", ")));
            item.put("lat", coordinates.get(1).asText());
            item.put("lon", coordinates.get(0).asText());
            item.put("type", properties.path("type").asText("place"));
        }
        return objectMapper.writeValueAsString(result);
    }

    private boolean claimProviderSlot() {
        if (redis != null) {
            try { return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(RATE_KEY, "1", Duration.ofSeconds(1))); }
            catch (RuntimeException ignored) { /* fallback vẫn giới hạn trong pod */ }
        }
        long now = System.currentTimeMillis();
        while (true) {
            long next = localNextAllowedAt.get();
            if (now < next) return false;
            if (localNextAllowedAt.compareAndSet(next, now + 1_000)) return true;
        }
    }
}
