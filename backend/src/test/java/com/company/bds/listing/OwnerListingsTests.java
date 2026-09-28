package com.company.bds.listing;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.QueryCount;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** My-listings v2 (F08.1, F08.6, UI-07 backend) and the USER → OWNER upgrade (P-09). */
@BdsIntegrationTest
class OwnerListingsTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    String bearer(UUID userId) { return "Bearer " + data.sessionFor(userId); }

    JsonNode page(String auth, String query) throws Exception {
        return json.readTree(mockMvc.perform(get("/api/v2/me/listings" + query).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void pagesAreStableBoundedAndCountedPerStatusWithoutNPlusOne() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        String auth = bearer(owner.id());
        Instant base = Instant.parse("2026-09-01T00:00:00Z");
        List<UUID> expectedOrder = new ArrayList<>();
        String[] statuses = {"ACTIVE", "ACTIVE", "DRAFT", "PENDING_REVIEW", "REJECTED", "EXPIRED", "PAUSED"};
        for (int i = 0; i < 21; i++) {
            // Two listings share each timestamp: the id breaks the tie.
            TestData.TestListing l = data.listing(owner.id()).status(statuses[i % statuses.length])
                    .title("Tin số " + i + " của chủ nhà").media(i % 3).createdAt(base.plusSeconds(i / 2 * 60)).create();
            expectedOrder.add(l.id());
        }
        expectedOrder.sort((a, b) -> {
            Instant ta = jdbc.queryForObject("SELECT created_at FROM listings WHERE id=?", java.sql.Timestamp.class, a).toInstant();
            Instant tb = jdbc.queryForObject("SELECT created_at FROM listings WHERE id=?", java.sql.Timestamp.class, b).toInstant();
            int byTime = tb.compareTo(ta);
            return byTime != 0 ? byTime : b.toString().compareTo(a.toString());
        });
        data.listing(data.user().role("OWNER").create().id()).create(); // someone else's listing never shows up

        List<String> seen = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            JsonNode body = page(auth, "?page=" + p + "&size=8");
            assertThat(body.get("total").asLong()).isEqualTo(21);
            assertThat(body.get("totalPages").asInt()).isEqualTo(3);
            body.get("items").forEach(item -> seen.add(item.get("id").asText()));
        }
        assertThat(seen).containsExactlyElementsOf(expectedOrder.stream().map(UUID::toString).toList());

        JsonNode counts = page(auth, "?size=1").get("counts");
        assertThat(counts.get("ALL").asLong()).isEqualTo(21);
        assertThat(counts.get("ACTIVE").asLong()).isEqualTo(6);
        assertThat(counts.get("DRAFT").asLong()).isEqualTo(3);
        assertThat(counts.get("LOCKED").asLong()).isZero();

        JsonNode active = page(auth, "?status=ACTIVE&size=50");
        assertThat(active.get("total").asLong()).isEqualTo(6);
        active.get("items").forEach(item -> {
            assertThat(item.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(item.get("publicVersion").get("price").get("currency").asText()).isEqualTo("VND");
            assertThat(item.get("pendingEdit").isNull()).isTrue();
            assertThat(item.get("quality").get("items").size()).isGreaterThanOrEqualTo(5);
        });
        JsonNode draft = page(auth, "?status=DRAFT&size=1").get("items").get(0);
        assertThat(draft.get("publicVersion").isNull()).isTrue();
        assertThat(draft.get("pendingEdit").get("status").asText()).isEqualTo("DRAFT");

        long small = QueryCount.count(() -> mockMvc.perform(get("/api/v2/me/listings?size=2").header("Authorization", auth)));
        long large = QueryCount.count(() -> mockMvc.perform(get("/api/v2/me/listings?size=20").header("Authorization", auth)));
        assertThat(large).isEqualTo(small);

        mockMvc.perform(get("/api/v2/me/listings?size=500").header("Authorization", auth)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v2/me/listings?status=SOLD").header("Authorization", auth)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v2/me/listings").header("Authorization", bearer(data.user().create().id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectedEditIsShownNextToThePublicVersionWithItsReason() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestListing listing = data.listing(owner.id()).title("Nhà phố đang hiển thị").create();
        UUID edit = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,
                    created_at,submitted_at,moderated_at,moderation_note)
                VALUES (?,?,2,'REJECTED','Nhà phố bản sửa','SALE','HOUSE',1,70,now(),now(),now(),'Ảnh không đúng bất động sản')
                """, edit, listing.id());
        JsonNode item = page(bearer(owner.id()), "?status=ACTIVE&size=50").get("items").get(0);
        assertThat(item.get("publicVersion").get("title").asText()).isEqualTo("Nhà phố đang hiển thị");
        assertThat(item.get("pendingEdit").get("revisionId").asText()).isEqualTo(edit.toString());
        assertThat(item.get("pendingEdit").get("status").asText()).isEqualTo("REJECTED");
        assertThat(item.get("pendingEdit").get("rejectionReason").asText()).isEqualTo("Ảnh không đúng bất động sản");
        mockMvc.perform(get("/api/v2/me/listings/" + listing.id() + "/draft").header("Authorization", bearer(owner.id())))
                .andExpect(jsonPath("$.revisionStatus").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Ảnh không đúng bất động sản"))
                .andExpect(jsonPath("$.hasPublicVersion").value(true));
    }

    @Test
    void qualityChecklistComparesPricePerM2OnlyWithEnoughComparables() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        String district = "9" + (int) (Math.random() * 90 + 10);
        TestData.TestListing mine = data.listing(owner.id()).status("DRAFT").district(district, "Quận kiểm thử")
                .price(30_000_000_000L).area("60").create();
        JsonNode items = page(bearer(owner.id()), "?status=DRAFT&size=50").get("items");
        JsonNode price = find(items, mine.id(), "PRICE_PLAUSIBILITY");
        assertThat(price.get("status").asText()).isEqualTo("NO_DATA");
        assertThat(price.get("hint").asText()).contains("Chưa đủ dữ liệu");

        TestData.TestUser other = data.user().role("BROKER").create();
        for (int i = 0; i < 10; i++) data.listing(other.id()).district(district, "Quận kiểm thử").price(3_000_000_000L + i).area("60").create();
        price = find(page(bearer(owner.id()), "?status=DRAFT&size=50").get("items"), mine.id(), "PRICE_PLAUSIBILITY");
        assertThat(price.get("status").asText()).isEqualTo("WARN");
        assertThat(price.get("hint").asText()).contains("cao").contains("10 tin");
    }

    private static JsonNode find(JsonNode items, UUID id, String code) {
        for (JsonNode item : items) {
            if (!item.get("id").asText().equals(id.toString())) continue;
            for (JsonNode q : item.get("quality").get("items")) if (q.get("code").asText().equals(code)) return q;
        }
        throw new AssertionError("no " + code + " for " + id);
    }

    @Test
    void userBecomesOwnerAfterExplicitConfirmationAndTheChangeIsAudited() throws Exception {
        TestData.TestUser user = data.user().create();
        String auth = bearer(user.id());
        mockMvc.perform(post("/api/v1/listings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/me/become-owner").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/me/become-owner").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmOwnProperty\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UPGRADED"))
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.user.roleLabel").value("Chủ nhà"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", auth))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.roleLabel").value("Chủ nhà"));
        mockMvc.perform(get("/api/v2/me/listings").header("Authorization", auth)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/become-owner").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmOwnProperty\":true}"))
                .andExpect(jsonPath("$.outcome").value("ALREADY_OWNER"));
        assertThat(jdbc.queryForList("SELECT from_role || '>' || to_role FROM user_role_changes WHERE user_id=?", String.class, user.id()))
                .containsExactly("USER>OWNER");
        assertThat(jdbc.queryForList("SELECT role FROM user_roles WHERE user_id=?", String.class, user.id())).containsExactly("OWNER");

        String broker = bearer(data.user().role("BROKER").create().id());
        mockMvc.perform(post("/api/v1/me/become-owner").header("Authorization", broker).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmOwnProperty\":true}")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/me/become-owner").contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmOwnProperty\":true}")).andExpect(status().isUnauthorized());
    }

    @Test
    void v2OwnerRoutesNeverCacheAndRequireAPoster() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        TestData.TestListing l = data.listing(owner.id()).create();
        mockMvc.perform(get("/api/v2/me/listings/" + l.id() + "/preview")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v2/me/listings/" + l.id() + "/preview").header("Authorization", bearer(owner.id())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(post("/api/v2/me/listings/" + l.id() + "/confirm-availability")
                .header("Authorization", bearer(data.user().role("OWNER").create().id()))).andExpect(status().isForbidden());
        assertThat(Duration.ofDays(45)).isEqualTo(com.company.bds.listing.domain.model.FreshnessPolicy.VALIDITY);
    }
}
