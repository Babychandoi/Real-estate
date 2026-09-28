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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Review 2: the dry run applies the commit's KYC and quota rules; re-import after deleting drafts; formula prefixes. */
@BdsIntegrationTest(properties = {"app.billing.quota-enforced=true", "app.listing.require-verified-kyc=true"})
class ListingImportEligibilityTests {
    static final String CSV = "title,purpose,propertyType,priceVnd,areaM2,description\n"
            + "=HYPERLINK(\"x\") Căn hộ hai phòng ngủ,SALE,APARTMENT,3950000000,70,\"- Căn góc\"\n"
            + "Căn hộ ba phòng ngủ Cầu Giấy,SALE,APARTMENT,5000000000,90,+cmd\n";

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
    void dryRunReportsMissingKycAndQuotaLikeTheCommit() throws Exception {
        TestData.TestUser noKyc = data.user().role("OWNER").plan("FREE", 5).create();
        JsonNode dry = upload("Bearer " + data.sessionFor(noKyc.id()), CSV, true, status().isOk());
        assertThat(dry.get("fileErrors").findValuesAsText("message")).anyMatch(m -> m.contains("eKYC"));

        TestData.TestUser oneLeft = data.user().role("OWNER").verifiedKyc().plan("FREE", 1).create();
        dry = upload("Bearer " + data.sessionFor(oneLeft.id()), CSV, true, status().isOk());
        assertThat(dry.get("validRows").asInt()).isEqualTo(1);
        assertThat(dry.get("rows").get(1).get("errors").findValuesAsText("message")).anyMatch(m -> m.contains("lượt"));
        upload("Bearer " + data.sessionFor(oneLeft.id()), CSV, false, status().isUnprocessableEntity());
    }

    @Test
    void formulaPrefixesAreRemovedAndAFileCanBeImportedAgainOnceItsDraftsAreGone() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").verifiedKyc().plan("FREE", 5).create();
        String auth = "Bearer " + data.sessionFor(owner.id());
        upload(auth, CSV, false, status().isCreated());
        assertThat(jdbc.queryForList("SELECT r.title || '|' || r.description FROM listing_revisions r JOIN listings l ON l.id=r.listing_id "
                + "WHERE l.owner_id=? ORDER BY r.price_vnd", String.class, owner.id()))
                .containsExactly("HYPERLINK(\"x\") Căn hộ hai phòng ngủ|- Căn góc", "Căn hộ ba phòng ngủ Cầu Giấy|cmd");
        assertThat(upload(auth, CSV, false, status().isOk()).get("duplicate").asBoolean()).isTrue();

        jdbc.update("DELETE FROM listing_media WHERE revision_id IN (SELECT r.id FROM listing_revisions r JOIN listings l ON l.id=r.listing_id WHERE l.owner_id=?)", owner.id());
        jdbc.update("DELETE FROM listing_revisions WHERE listing_id IN (SELECT id FROM listings WHERE owner_id=?)", owner.id());
        jdbc.update("DELETE FROM listings WHERE owner_id=?", owner.id());
        assertThat(upload(auth, CSV, false, status().isCreated()).get("duplicate").asBoolean()).isFalse();
    }
}
