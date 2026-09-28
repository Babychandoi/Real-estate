package com.company.bds.search.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Signed, expiring page cursors (contract §8): {@code base64url(json{v,e,s,k,h,x}) + "." + base64url(HMAC-SHA256)}.
 * The key comes from {@code app.search.cursor-secret} (env {@code SEARCH_CURSOR_SECRET}, required in production by
 * {@code ProductionSafetyValidator}); without it a random per-process key is used, so cursors survive only until a
 * restart and are not portable between instances (fine for development and tests only).
 */
@Component
public class SearchCursorCodec {
    public static final Duration TTL = Duration.ofMinutes(30);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** Decoded cursor: engine, sort, last sort values, filter hash. */
    public record Cursor(String engine, String sort, ArrayNode keys, String filterHash) {}

    private final ObjectMapper json;
    private final Clock clock;
    private final byte[] key;

    public SearchCursorCodec(ObjectMapper json, Clock clock, @Value("${app.search.cursor-secret:}") String secret) {
        this.json = json;
        this.clock = clock;
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.key = random;
        } else {
            this.key = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String encode(String engine, String sort, ArrayNode keys, String filterHash) {
        ObjectNode body = json.createObjectNode();
        body.put("v", 1);
        body.put("e", engine);
        body.put("s", sort);
        body.set("k", keys);
        body.put("h", filterHash);
        body.put("x", clock.instant().plus(TTL).getEpochSecond());
        try {
            String payload = ENCODER.encodeToString(json.writeValueAsBytes(body));
            return payload + "." + ENCODER.encodeToString(sign(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot encode search cursor", ex);
        }
    }

    /**
     * Verifies signature, version, expiry and filter hash.
     *
     * @throws SearchProblemException {@code 400 CURSOR_INVALID} when any check fails
     */
    public Cursor decode(String cursor, String expectedFilterHash) {
        int dot = cursor.indexOf('.');
        if (dot <= 0 || dot != cursor.lastIndexOf('.')) throw invalid("Con trỏ trang không hợp lệ.");
        String payload = cursor.substring(0, dot);
        byte[] signature;
        try {
            signature = DECODER.decode(cursor.substring(dot + 1));
        } catch (IllegalArgumentException ex) {
            throw invalid("Con trỏ trang không hợp lệ.");
        }
        if (!MessageDigest.isEqual(sign(payload), signature)) throw invalid("Con trỏ trang không hợp lệ.");
        JsonNode body;
        try {
            body = json.readTree(DECODER.decode(payload));
        } catch (Exception ex) {
            throw invalid("Con trỏ trang không hợp lệ.");
        }
        if (body.path("v").asInt() != 1 || !body.path("k").isArray()) throw invalid("Con trỏ trang không hợp lệ.");
        if (body.path("x").asLong() < clock.instant().getEpochSecond()) {
            throw invalid("Con trỏ trang đã hết hạn; hãy tải lại kết quả từ trang đầu.");
        }
        if (!expectedFilterHash.equals(body.path("h").asText())) {
            throw invalid("Con trỏ trang thuộc về một bộ lọc khác.");
        }
        return new Cursor(body.path("e").asText(), body.path("s").asText(), (ArrayNode) body.path("k"), body.path("h").asText());
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static SearchProblemException invalid(String message) {
        return new SearchProblemException(400, "CURSOR_INVALID", "Con trỏ trang không hợp lệ", message);
    }
}
