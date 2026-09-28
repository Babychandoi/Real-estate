package com.company.bds.listing;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Write side of listings (F04.1, F07.2, F07.3, R-4): structured attributes, field errors, lost-update protection. */
@BdsIntegrationTest
class ListingWritePathTests {
    static final String IMG = "https://images.unsplash.com/photo-1600585154340-be6161a56a0c";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    static String rentDraft(String extra) {
        return """
                {"purpose":"RENT","propertyType":"APARTMENT","title":"Cho thuê căn hộ hai phòng ngủ đủ đồ",
                 "priceVnd":14500000,"areaM2":70,"description":"Căn hộ thoáng, gần trường học.","districtCode":"005",
                 "addressSummary":"Cầu Giấy, Hà Nội","imageUrls":["%s"]%s}
                """.formatted(IMG, extra);
    }

    String bearer(UUID userId) { return "Bearer " + data.sessionFor(userId); }

    JsonNode create(String auth, String body) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/v1/listings").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void rentDraftStoresRentTermsFurnishingLegalCodeAndProjectAndTheOwnerReadsThemBack() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        String auth = bearer(owner.id());
        UUID project = UUID.randomUUID();
        jdbc.update("INSERT INTO projects(id,name,slug,developer_name,province_code,district_code,address,legal_license_number) VALUES (?,?,?,?,?,?,?,?)",
                project, "Dự án kiểm thử", "du-an-" + project, "Chủ đầu tư kiểm thử", "01", "005", "Cầu Giấy", "GP-TEST");
        JsonNode created = create(auth, rentDraft(",\"monthlyServiceFeeVnd\":1200000,\"depositVnd\":29000000,\"furnishing\":\"FULL\","
                + "\"legalStatusCode\":\"PINK_BOOK\",\"legalStatus\":\"Sổ hồng riêng\",\"projectId\":\"" + project + "\""));
        String id = created.get("listingId").asText();
        assertThat(created.get("version").asLong()).isZero();

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT r.monthly_service_fee_vnd, r.deposit_vnd, r.furnishing, r.legal_status_code, r.project_id, r.price_period, l.source
                FROM listing_revisions r JOIN listings l ON l.id = r.listing_id WHERE r.listing_id = ?::uuid""", id);
        assertThat(row).containsEntry("monthly_service_fee_vnd", 1_200_000L).containsEntry("deposit_vnd", 29_000_000L)
                .containsEntry("furnishing", "FULL").containsEntry("legal_status_code", "PINK_BOOK")
                .containsEntry("project_id", project).containsEntry("price_period", "MONTH").containsEntry("source", "DIRECT");

        mockMvc.perform(get("/api/v2/me/listings/" + id + "/draft").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.depositVnd").value(29_000_000))
                .andExpect(jsonPath("$.monthlyServiceFeeVnd").value(1_200_000))
                .andExpect(jsonPath("$.furnishing").value("FULL"))
                .andExpect(jsonPath("$.legalStatusCode").value("PINK_BOOK"))
                .andExpect(jsonPath("$.imageUrls[0]").value(IMG))
                .andExpect(jsonPath("$.quality.items[?(@.code=='RENT_TERMS')].status").value("PASS"));

        mockMvc.perform(get("/api/v2/me/listings/" + id + "/preview").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(14_500_000))
                .andExpect(jsonPath("$.price.period").value("MONTH"))
                .andExpect(jsonPath("$.unitPrice").doesNotExist())
                .andExpect(jsonPath("$.rentTerms.deposit").value(29_000_000))
                .andExpect(jsonPath("$.legal.label").value("Sổ hồng"))
                .andExpect(jsonPath("$.images[0].url").value(IMG));

        // Switching the draft to SALE with the old rent terms is a field error, without them the terms are gone.
        String sale = rentDraft(",\"depositVnd\":29000000").replace("\"RENT\"", "\"SALE\"").replace("14500000", "3950000000");
        mockMvc.perform(put("/api/v1/listings/" + id + "/draft").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(sale))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("depositVnd"));
        mockMvc.perform(put("/api/v1/listings/" + id + "/draft").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(rentDraft("").replace("\"RENT\"", "\"SALE\"")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT deposit_vnd, monthly_service_fee_vnd, price_period FROM listing_revisions WHERE listing_id=?::uuid", id))
                .containsEntry("deposit_vnd", null).containsEntry("monthly_service_fee_vnd", null).containsEntry("price_period", null);
    }

    @Test
    void invalidValuesAreFieldErrorsNot500() throws Exception {
        String auth = bearer(data.user().role("OWNER").create().id());
        mockMvc.perform(post("/api/v1/listings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(rentDraft(",\"furnishing\":\"LUXURY\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("furnishing"));
        mockMvc.perform(post("/api/v1/listings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(rentDraft(",\"legalStatusCode\":\"OTHER\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("legalStatus"));
        mockMvc.perform(post("/api/v1/listings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(rentDraft(",\"projectId\":\"" + UUID.randomUUID() + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("projectId"));
        mockMvc.perform(post("/api/v1/listings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(rentDraft(",\"depositVnd\":-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("depositVnd"));
    }

    @Test
    void staleVersionGets409AndTheCurrentEtag() throws Exception {
        String auth = bearer(data.user().role("OWNER").create().id());
        String id = create(auth, rentDraft("")).get("listingId").asText();
        mockMvc.perform(put("/api/v1/listings/" + id + "/draft").header("Authorization", auth).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(rentDraft("").replace("hai phòng", "2 phòng")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(put("/api/v1/listings/" + id + "/draft").header("Authorization", auth).header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(rentDraft("").replace("hai phòng", "ba phòng")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(header().string("ETag", "\"v1\""));
        assertThat(jdbc.queryForObject("SELECT title FROM listing_revisions WHERE listing_id=?::uuid", String.class, id))
                .contains("2 phòng");
        // expectedVersion in the body works like If-Match for clients that cannot set headers.
        mockMvc.perform(put("/api/v1/listings/" + id + "/draft").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(rentDraft(",\"expectedVersion\":0")))
                .andExpect(status().isConflict());
    }

    @Test
    void otherAccountsGet404WithoutLearningTheVersion() throws Exception {
        String auth = bearer(data.user().role("OWNER").create().id());
        String id = create(auth, rentDraft("")).get("listingId").asText();
        String other = bearer(data.user().role("OWNER").create().id());
        for (String ifMatch : new String[]{"\"v0\"", "\"v7\""}) {
            mockMvc.perform(put("/api/v1/listings/" + id + "/draft").header("Authorization", other).header("If-Match", ifMatch)
                            .contentType(MediaType.APPLICATION_JSON).content(rentDraft("")))
                    .andExpect(status().isNotFound())
                    .andExpect(header().doesNotExist("ETag"));
        }
        for (String path : new String[]{"/draft", "/preview"}) {
            mockMvc.perform(get("/api/v2/me/listings/" + id + path).header("Authorization", other)).andExpect(status().isNotFound());
        }
        for (String path : new String[]{"/confirm-availability", "/renew"}) {
            mockMvc.perform(post("/api/v2/me/listings/" + id + path).header("Authorization", other)).andExpect(status().isNotFound());
        }
    }

    @Test
    void concurrentSavesOfTheSameVersionOneWinsTheOtherGets409() throws Exception {
        String auth = bearer(data.user().role("OWNER").create().id());
        String id = create(auth, rentDraft("")).get("listingId").asText();
        int writers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            String title = "Cho thuê căn hộ phiên bản số " + i;
            Callable<Integer> call = () -> {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/v1/listings/" + id + "/draft").header("Authorization", auth)
                        .header("If-Match", "\"v0\"").contentType(MediaType.APPLICATION_JSON)
                        .content(rentDraft("").replace("Cho thuê căn hộ hai phòng ngủ đủ đồ", title))).andReturn();
                return r.getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(writers - 1);
        assertThat(jdbc.queryForObject("SELECT version FROM listings WHERE id=?::uuid", Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing_revisions WHERE listing_id=?::uuid", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void editOfAPublishedListingKeepsThePublicVersionVisibleUntilApprovedAndApprovalStartsValidity() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        String auth = bearer(owner.id());
        TestData.TestListing listing = data.listing(owner.id()).title("Căn hộ đang hiển thị công khai").create();
        long version = jdbc.queryForObject("SELECT version FROM listings WHERE id=?", Long.class, listing.id());
        mockMvc.perform(put("/api/v1/listings/" + listing.id() + "/draft").header("Authorization", auth)
                        .header("If-Match", "\"v" + version + "\"").contentType(MediaType.APPLICATION_JSON)
                        .content(rentDraft("").replace("\"RENT\"", "\"SALE\"").replace("14500000", "3100000000")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/listings/" + listing.id() + "/submit").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        // Public page still shows the approved version; the edit is in the moderation queue.
        mockMvc.perform(get("/api/v1/listings/by-slug/" + listing.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Căn hộ đang hiển thị công khai"));
        String moderator = bearer(data.user().role("MODERATOR").create().id());
        String queue = mockMvc.perform(get("/api/v1/moderation/queue").header("Authorization", moderator))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(queue).contains(listing.id().toString());
        // Other posters cannot read the draft; staff can preview it.
        mockMvc.perform(get("/api/v2/me/listings/" + listing.id() + "/draft")
                        .header("Authorization", bearer(data.user().role("OWNER").create().id())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/me/listings/" + listing.id() + "/preview").header("Authorization", moderator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionStatus").value("SUBMITTED"));

        UUID submitted = jdbc.queryForObject("SELECT id FROM listing_revisions WHERE listing_id=? AND status='SUBMITTED'", UUID.class, listing.id());
        mockMvc.perform(post("/api/v1/moderation/listings/" + listing.id() + "/approve").header("Authorization", moderator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revisionId\":\"" + submitted + "\"}"))
                .andExpect(status().isOk());
        Map<String, Object> after = jdbc.queryForMap("""
                SELECT status, public_revision_id, EXTRACT(EPOCH FROM expires_at - availability_confirmed_at)::bigint AS validity
                FROM listings WHERE id=?""", listing.id());
        assertThat(after).containsEntry("status", "ACTIVE").containsEntry("public_revision_id", submitted)
                .containsEntry("validity", 45L * 24 * 3600);
        // listing_published is recorded once per published revision (S0-BE, approval path), never by the write path.
        assertOnePublished(listing.id());
        mockMvc.perform(post("/api/v1/moderation/listings/" + listing.id() + "/approve").header("Authorization", moderator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"revisionId\":\"" + submitted + "\"}"));
        assertOnePublished(listing.id());
    }

    void assertOnePublished(UUID listingId) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_events WHERE name='listing_published' AND listing_id=?",
                Integer.class, listingId)).isEqualTo(1);
    }
}
