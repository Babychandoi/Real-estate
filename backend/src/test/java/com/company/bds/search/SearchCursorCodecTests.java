package com.company.bds.search;

import com.company.bds.search.application.SearchCursorCodec;
import com.company.bds.search.application.SearchProblemException;
import com.company.bds.testsupport.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Signed cursors of contract §8 (F06.2): tamper, expiry, filter hash, secret. */
class SearchCursorCodecTests {
    private final ObjectMapper json = new ObjectMapper();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-28T00:00:00Z"));
    private final SearchCursorCodec codec = new SearchCursorCodec(json, clock, "s2-test-cursor-secret-0123456789abcdef");

    private ArrayNode keys() {
        return json.createArrayNode().add(1_758_000_000_000_000L).add("0f7c5b8e-0000-4000-8000-000000000001");
    }

    @Test
    void roundTripKeepsEngineSortAndKeys() {
        String cursor = codec.encode("database", "NEWEST", keys(), "hash-a");
        SearchCursorCodec.Cursor decoded = codec.decode(cursor, "hash-a");
        assertThat(decoded.engine()).isEqualTo("database");
        assertThat(decoded.sort()).isEqualTo("NEWEST");
        assertThat(decoded.keys()).isEqualTo(keys());
    }

    @Test
    void tamperedPayloadOrSignatureIsRejected() throws Exception {
        String cursor = codec.encode("search", "PRICE_ASC", keys(), "hash-a");
        String payload = cursor.substring(0, cursor.indexOf('.'));
        String body = new String(Base64.getUrlDecoder().decode(payload)).replace("PRICE_ASC", "PRICE_DESC");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes()) + cursor.substring(cursor.indexOf('.'));
        assertInvalid(() -> codec.decode(forged, "hash-a"));
        assertInvalid(() -> codec.decode(cursor.substring(0, cursor.length() - 2) + "xx", "hash-a"));
        assertInvalid(() -> codec.decode("garbage", "hash-a"));
        assertInvalid(() -> codec.decode(cursor + ".extra", "hash-a"));
        SearchCursorCodec otherSecret = new SearchCursorCodec(json, clock, "another-secret-another-secret-0000");
        assertInvalid(() -> otherSecret.decode(cursor, "hash-a"));
    }

    @Test
    void expiredAfterThirtyMinutes() {
        String cursor = codec.encode("database", "NEWEST", keys(), "hash-a");
        clock.advance(Duration.ofMinutes(29));
        assertThat(codec.decode(cursor, "hash-a").engine()).isEqualTo("database");
        clock.advance(Duration.ofMinutes(2));
        assertInvalid(() -> codec.decode(cursor, "hash-a"));
    }

    @Test
    void boundToTheFilterHash() {
        String cursor = codec.encode("database", "NEWEST", keys(), "hash-a");
        assertInvalid(() -> codec.decode(cursor, "hash-b"));
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(SearchProblemException.class, ex -> {
            assertThat(ex.status()).isEqualTo(400);
            assertThat(ex.code()).isEqualTo("CURSOR_INVALID");
        });
    }
}
