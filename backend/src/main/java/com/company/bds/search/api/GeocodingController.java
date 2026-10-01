package com.company.bds.search.api;

import com.company.bds.iam.application.AuthService;
import com.company.bds.shared.redis.RedisCircuitBreaker;
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
    private static final String REDIS_CALLER = "geocoding";
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final RedisCircuitBreaker redisBreaker;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicLong localNextAllowedAt = new AtomicLong();
    private final String providerUrl;
    private final String contact;
    private final ObjectMapper objectMapper;
    private final java.time.Clock clock;
    private final Duration positiveTtl;
    private final Duration negativeTtl;

    public GeocodingController(JdbcTemplate jdbc, ObjectProvider<StringRedisTemplate> redisProvider,
            RedisCircuitBreaker redisBreaker, ObjectMapper objectMapper, java.time.Clock clock,
            @Value("${app.geocoding.provider-url:https://photon.komoot.io}") String providerUrl,
            @Value("${app.geocoding.contact:}") String contact,
            @Value("${app.geocoding.cache-ttl:P14D}") Duration positiveTtl,
            @Value("${app.geocoding.negative-cache-ttl:PT1H}") Duration negativeTtl) {
        this.jdbc = jdbc;
        this.redis = redisProvider.getIfAvailable();
        this.redisBreaker = redisBreaker;
        this.providerUrl = providerUrl.replaceAll("/+$", "");
        this.contact = contact;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.positiveTtl = positiveTtl;
        this.negativeTtl = negativeTtl;
    }

    /**
     * Cache key text (audit F10.3): NFC, lower case (vi), whitespace collapsed, surrounding punctuation trimmed.
     * Diacritics are kept on purpose: the provider answers "Hà Nam" and "Hà Nậm" differently.
     */
    public static String normalizedKey(String query) {
        String nfc = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFC)
                .toLowerCase(java.util.Locale.forLanguageTag("vi"));
        return nfc.replaceAll("\\s+", " ").replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
    }

    @GetMapping
    public String search(@RequestParam String q) throws Exception {
        String query = q.trim();
        if (query.length() < 3 || query.length() > 250) {
            throw new IllegalArgumentException("Địa chỉ phải dài từ 3 đến 250 ký tự.");
        }
        String normalized = normalizedKey(query);
        if (normalized.length() < 3) throw new IllegalArgumentException("Địa chỉ phải dài từ 3 đến 250 ký tự.");
        String provider = providerUrl.contains("photon.komoot.io") ? "photon" : providerUrl;
        String hash = AuthService.sha256("v3|" + provider + "|vi|" + normalized);
        var now = java.sql.Timestamp.from(clock.instant());
        var cached = jdbc.queryForList("SELECT response_json FROM geocode_cache WHERE query_hash=? AND expires_at > ?",
                String.class, hash, now);
        if (!cached.isEmpty()) return cached.get(0);
        if (!claimProviderSlot()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Dịch vụ định vị đang giới hạn tần suất; vui lòng thử lại sau một giây.");
        }
        String userAgent = "NhaDatChuan/1.0" + (contact.isBlank() ? "" : " (" + contact + ")");
        boolean photon = providerUrl.contains("photon.komoot.io");
        String endpoint = photon
                ? providerUrl + "/api?q=" + URLEncoder.encode(query + ", Vietnam", StandardCharsets.UTF_8)
                        + "&limit=8&lat=21.0285&lon=105.8542"
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
        boolean negative = objectMapper.readTree(body).isEmpty();
        var expires = java.sql.Timestamp.from(clock.instant().plus(negative ? negativeTtl : positiveTtl));
        String stored = query.length() > 300 ? query.substring(0, 300) : query;
        jdbc.update("""
                INSERT INTO geocode_cache(query_hash, query_text, response_json, normalized_query, provider, negative, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (query_hash) DO UPDATE SET response_json = EXCLUDED.response_json, negative = EXCLUDED.negative,
                    created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at
                """, hash, stored, body, normalized.length() > 300 ? normalized.substring(0, 300) : normalized, provider,
                negative, now, expires);
        return body;
    }

    private String normalizePhoton(String body) throws IOException {
        ArrayNode result = objectMapper.createArrayNode();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JsonNode feature : objectMapper.readTree(body).path("features")) {
            JsonNode coordinates = feature.path("geometry").path("coordinates");
            JsonNode properties = feature.path("properties");
            if (coordinates.size() < 2) continue;
            String countryCode = properties.path("countrycode").asText("VN");
            if (!"VN".equalsIgnoreCase(countryCode)) continue;
            String displayName = java.util.stream.Stream.of("name", "street", "locality", "district", "county", "city", "state")
                    .map(key -> properties.path(key).asText(""))
                    .filter(value -> !value.isBlank()).distinct()
                    .collect(java.util.stream.Collectors.joining(", "));
            if (displayName.isBlank() || !seen.add(displayName)) continue;
            ObjectNode item = result.addObject();
            item.put("display_name", displayName);
            item.put("lat", coordinates.get(1).asText());
            item.put("lon", coordinates.get(0).asText());
            item.put("type", properties.path("type").asText("place"));
            JsonNode extent = properties.path("extent");
            if (extent.isArray() && extent.size() == 4) {
                // Photon extent is [minLon, maxLat, maxLon, minLat]; expose Nominatim order [minLat, maxLat, minLon, maxLon].
                ArrayNode box = item.putArray("boundingbox");
                box.add(extent.get(3).asText()).add(extent.get(1).asText()).add(extent.get(0).asText()).add(extent.get(2).asText());
            }
            if (result.size() >= 6) break;
        }
        return objectMapper.writeValueAsString(result);
    }

    private boolean claimProviderSlot() {
        if (redis != null && redisBreaker.tryAcquire(REDIS_CALLER)) {
            try {
                boolean claimed = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(RATE_KEY, "1", Duration.ofSeconds(1)));
                redisBreaker.recordSuccess();
                return claimed;
            } catch (RuntimeException ex) {
                redisBreaker.recordFailure(REDIS_CALLER, ex); // fallback vẫn giới hạn trong pod
            }
        }
        long now = System.currentTimeMillis();
        while (true) {
            long next = localNextAllowedAt.get();
            if (now < next) return false;
            if (localNextAllowedAt.compareAndSet(next, now + 1_000)) return true;
        }
    }
}
