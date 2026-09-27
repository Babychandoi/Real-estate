package com.company.bds.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@BdsIntegrationTest
class TestSupportTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    @Test
    void runsOnAFreshMigratedPostgresDatabase() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).matches("bds_it_\\d{10}_[0-9a-f]{8}");
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_extension WHERE extname IN ('postgis','unaccent','uuid-ossp')", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT bool_and(success) FROM flyway_schema_history", Boolean.class)).isTrue();
    }

    @Test
    void queryCountMeasuresStatementsOfTheCallingThread() {
        assertThat(QueryCount.count(() -> {
            jdbc.queryForObject("SELECT 1", Integer.class);
            jdbc.update("UPDATE users SET updated_at = updated_at WHERE FALSE");
        })).isEqualTo(2);

        assertThat(QueryCount.assertAtMost(1, () -> jdbc.queryForObject("SELECT 41 + 1", Integer.class))).isEqualTo(42);
        assertThatThrownBy(() -> QueryCount.assertAtMost(1, () -> {
            jdbc.queryForObject("SELECT 1", Integer.class);
            jdbc.queryForObject("SELECT 2", Integer.class);
        })).isInstanceOf(AssertionError.class).hasMessageContaining("at most 1").hasMessageContaining("2 were executed");
    }

    @Test
    void buildersCreateConsistentUsersListingsLeadsAndSessions() throws Exception {
        TestData.TestUser broker = data.user().role("BROKER").verifiedKyc().name("Môi giới fixture").create();
        TestData.TestListing listing = data.listing(broker.id()).purpose("RENT").price(14_500_000L).media(3).olderRevisions(2).create();
        UUID leadId = data.lead(listing.id()).status("CONTACTED").create();
        String token = data.sessionFor(broker.id());

        assertThat(listing.revisionIds()).hasSize(3);
        assertThat(listing.publicRevisionId()).isEqualTo(listing.latestRevisionId());
        mockMvc.perform(get("/api/v1/listings/" + listing.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.revisionNumber").value(3))
                .andExpect(jsonPath("$.purpose").value("RENT"))
                .andExpect(jsonPath("$.imageUrls.length()").value(3));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BROKER"))
                .andExpect(jsonPath("$.email").value(broker.email()));
        JsonNode leads = json.readTree(mockMvc.perform(get("/api/v1/leads").param("listingId", listing.id().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(leads.findValuesAsText("id")).containsExactly(leadId.toString());
        assertThat(leads.get(0).get("status").asText()).isEqualTo("CONTACTED");

        TestData.TestListing draft = data.listing(broker.id()).status("DRAFT").media(0).create();
        assertThat(draft.publicRevisionId()).isNull();
        mockMvc.perform(get("/api/v1/listings/" + draft.id())).andExpect(status().isNotFound());
    }

    @Test
    void databaseUrlKeepsHostAndQueryParameters() {
        assertThat(BdsTestDatabase.withDatabase("jdbc:postgresql://127.0.0.1:55432/bds_test_admin", "bds_it_x"))
                .isEqualTo("jdbc:postgresql://127.0.0.1:55432/bds_it_x");
        assertThat(BdsTestDatabase.withDatabase("jdbc:postgresql://db:5432/admin?sslmode=disable", "bds_it_y"))
                .isEqualTo("jdbc:postgresql://db:5432/bds_it_y?sslmode=disable");
    }
}
