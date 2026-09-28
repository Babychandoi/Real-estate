package com.company.bds.listing;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CSV import (P-08): dry run per-row validation, one-transaction commit, idempotent per file hash. */
@BdsIntegrationTest
class ListingImportTests {
    static final String HEADER = "title,purpose,propertyType,priceVnd,areaM2,bedrooms,legalStatusCode,legalStatus,furnishing,depositVnd,districtCode,addressSummary,description\n";
    static final String GOOD = HEADER
            + "\"Căn hộ 2 phòng ngủ, view hồ\",SALE,APARTMENT,3950000000,\"72,5\",2,PINK_BOOK,Sổ hồng riêng,BASIC,,005,Cầu Giấy,Mô tả\n"
            + "Cho thuê nhà nguyên căn gần chợ,RENT,HOUSE,14500000,60,3,,,FULL,29000000,005,Cầu Giấy,\"Dòng 1\nDòng 2\"\n";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    JsonNode upload(String auth, String csv, boolean dryRun, ResultMatcher expected) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "tin.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return json.readTree(mockMvc.perform(multipart("/api/v2/me/listings/import").file(file)
                        .param("dryRun", String.valueOf(dryRun)).header("Authorization", auth))
                .andExpect(expected).andReturn().getResponse().getContentAsString());
    }

    @Test
    void dryRunWritesNothingCommitCreatesImportDraftsOnceAndIsIdempotent() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        String auth = "Bearer " + data.sessionFor(owner.id());
        JsonNode dry = upload(auth, GOOD, true, status().isOk());
        assertThat(dry.get("validRows").asInt()).isEqualTo(2);
        assertThat(dry.get("committed").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE owner_id=?", Integer.class, owner.id())).isZero();

        JsonNode commit = upload(auth, GOOD, false, status().isCreated());
        assertThat(commit.get("committed").asBoolean()).isTrue();
        assertThat(commit.get("rows").get(0).get("listingId").isNull()).isFalse();
        assertThat(jdbc.queryForList("SELECT l.source || ':' || l.status || ':' || r.status FROM listings l "
                        + "JOIN listing_revisions r ON r.listing_id=l.id WHERE l.owner_id=?", String.class, owner.id()))
                .containsExactly("IMPORT:DRAFT:DRAFT", "IMPORT:DRAFT:DRAFT");
        assertThat(jdbc.queryForObject("SELECT deposit_vnd FROM listing_revisions r JOIN listings l ON l.id=r.listing_id "
                + "WHERE l.owner_id=? AND r.purpose='RENT'", Long.class, owner.id())).isEqualTo(29_000_000L);
        assertThat(jdbc.queryForObject("SELECT description FROM listing_revisions r JOIN listings l ON l.id=r.listing_id "
                + "WHERE l.owner_id=? AND r.purpose='RENT'", String.class, owner.id())).isEqualTo("Dòng 1\nDòng 2");

        JsonNode again = upload(auth, GOOD, false, status().isOk());
        assertThat(again.get("duplicate").asBoolean()).isTrue();
        assertThat(again.get("batchId").asText()).isEqualTo(commit.get("batchId").asText());
        assertThat(again.get("rows").get(1).get("listingId").asText()).isEqualTo(commit.get("rows").get(1).get("listingId").asText());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE owner_id=?", Integer.class, owner.id())).isEqualTo(2);

        // Another owner may import the same bytes: idempotency is per owner.
        String other = "Bearer " + data.sessionFor(data.user().role("BROKER").create().id());
        assertThat(upload(other, GOOD, false, status().isCreated()).get("duplicate").asBoolean()).isFalse();
    }

    @Test
    void everyRowErrorIsReportedAndACommitWithErrorsCreatesNothing() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        String auth = "Bearer " + data.sessionFor(owner.id());
        String bad = HEADER
                + "Ngắn,SALE,APARTMENT,abc,0,2,,,,,005,,\n"
                + "Bán căn hộ gọi 0912345678 ngay,SALE,CASTLE,3000000000,70,2,OTHER,,,5000000,005,,\n"
                + "\"Căn hộ hợp lệ trong tệp lỗi\",SALE,APARTMENT,3000000000,70,2,,,,,005,,\n";
        JsonNode dry = upload(auth, bad, true, status().isOk());
        assertThat(dry.get("totalRows").asInt()).isEqualTo(3);
        assertThat(dry.get("validRows").asInt()).isEqualTo(1);
        JsonNode first = dry.get("rows").get(0);
        assertThat(first.get("line").asInt()).isEqualTo(2);
        assertThat(first.get("errors").findValuesAsText("field")).contains("title", "priceVnd", "areaM2");
        assertThat(dry.get("rows").get(1).get("errors").findValuesAsText("field"))
                .contains("title", "propertyType", "legalStatus", "depositVnd");
        assertThat(dry.get("rows").get(2).get("status").asText()).isEqualTo("VALID");

        JsonNode commit = upload(auth, bad, false, status().isUnprocessableEntity());
        assertThat(commit.get("committed").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE owner_id=?", Integer.class, owner.id())).isZero();

        JsonNode missingColumn = upload(auth, "title,purpose\nabc,SALE\n", true, status().isOk());
        assertThat(missingColumn.get("fileErrors").findValuesAsText("field")).contains("propertyType", "priceVnd", "areaM2");
    }

    @Test
    void templateDownloadsAndPlainUsersCannotImport() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        String body = mockMvc.perform(get("/api/v2/me/listings/import/template").header("Authorization", "Bearer " + data.sessionFor(owner.id())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).startsWith("title,purpose,propertyType,priceVnd,areaM2");
        upload("Bearer " + data.sessionFor(data.user().create().id()), GOOD, true, status().isForbidden());
    }
}
