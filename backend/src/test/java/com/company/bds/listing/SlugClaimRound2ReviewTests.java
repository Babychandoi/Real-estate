package com.company.bds.listing;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Round-2 review of the non-blocking slug claim (PR #24, bafe510). Advisory locks are re-entrant within a session, so one
 * import claiming the same title twice gets "true" both times; this pins that the JPA auto-flush still makes the second
 * row see the first (verified correct, expected to pass).
 */
@BdsIntegrationTest
class SlugClaimRound2ReviewTests {
    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void oneImportWithTheSameTitleOnSeveralRowsCommitsWithDistinctSlugs() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        String title = "Nhà trùng tiêu đề " + UUID.randomUUID().toString().substring(0, 8);
        String row = "\"" + title + "\",SALE,APARTMENT,3950000000,\"72,5\",2,PINK_BOOK,Sổ hồng riêng,BASIC,,005,Cầu Giấy,Mô tả\n";
        String csv = ListingImportTests.HEADER + row + row + row;
        MockHttpServletResponse response = mvc.perform(multipart("/api/v2/me/listings/import")
                .file(new MockMultipartFile("file", "tin.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", "false").header("Authorization", "Bearer " + data.sessionFor(owner.id()))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(201);
        JsonNode body = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("committed").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT slug) FROM listings WHERE owner_id = ?", Integer.class, owner.id())).isEqualTo(3);
    }
}
