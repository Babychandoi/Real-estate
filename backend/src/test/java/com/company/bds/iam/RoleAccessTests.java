package com.company.bds.iam;

import com.company.bds.shared.security.Roles;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OWNER persona (P-09) and role priority ADMIN > MODERATOR > BROKER > OWNER > USER (contract §2.5). */
@BdsIntegrationTest
class RoleAccessTests {
    private static final String DRAFT = """
            {"purpose":"SALE","propertyType":"HOUSE","title":"Nhà riêng chủ nhà tự đăng bán",
             "priceVnd":2500000000,"areaM2":60,"description":"Chủ nhà đăng tin kiểm thử quyền.","addressSummary":"Hà Nội"}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    @Test
    void ownerRegistersAndCanPostAndManageListingsButNotUseBrokerOrStaffAreas() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s","name":"Chủ nhà kiểm thử","accountType":"OWNER"}
                        """.formatted(email, TestData.DEFAULT_PASSWORD)))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForList("SELECT ur.role FROM user_roles ur JOIN users u ON u.id = ur.user_id WHERE u.email = ?",
                String.class, email)).containsExactly(Roles.OWNER);
        jdbc.update("UPDATE users SET status='ACTIVE', email_verified_at=CURRENT_TIMESTAMP WHERE email=?", email);
        JsonNode login = json.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestData.DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value(Roles.OWNER))
                .andReturn().getResponse().getContentAsString());
        String bearer = "Bearer " + login.get("accessToken").asText();

        String listingId = json.readTree(mockMvc.perform(post("/api/v1/listings").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString()).get("listingId").asText();
        mockMvc.perform(put("/api/v1/listings/" + listingId + "/draft").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(DRAFT.replace("tự đăng bán", "tự đăng bán, sổ đỏ")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/listings/" + listingId + "/submit").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        mockMvc.perform(get("/api/v1/listings/my-listings").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(listingId));
        mockMvc.perform(get("/api/v1/billing/orders").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/leads").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/leads/listings").header("Authorization", bearer)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/broker/workspace").header("Authorization", bearer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/moderation/queue").header("Authorization", bearer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestData.DEFAULT_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void plainUserCannotPostListingsWhileOwnerCan() throws Exception {
        String userBearer = "Bearer " + data.sessionFor(data.user().role(Roles.USER).create().id());
        String ownerBearer = "Bearer " + data.sessionFor(data.user().role(Roles.OWNER).create().id());

        mockMvc.perform(post("/api/v1/listings").header("Authorization", userBearer).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/listings/my-listings").header("Authorization", userBearer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/billing/orders").header("Authorization", userBearer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/leads").header("Authorization", userBearer)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/listings").header("Authorization", ownerBearer).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isCreated());
    }

    @Test
    void ownerSeesOnlyTheirOwnLeadsAndCannotMarkThemWithdrawn() throws Exception {
        TestData.TestUser owner = data.user().role(Roles.OWNER).verifiedKyc().create();
        TestData.TestUser otherOwner = data.user().role(Roles.OWNER).create();
        TestData.TestListing listing = data.listing(owner.id()).create();
        TestData.TestListing otherListing = data.listing(otherOwner.id()).create();
        UUID leadId = data.lead(listing.id()).create();
        data.lead(otherListing.id()).create();
        String bearer = "Bearer " + data.sessionFor(owner.id());

        JsonNode inbox = json.readTree(mockMvc.perform(get("/api/v1/leads").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(inbox.findValuesAsText("id")).containsExactly(leadId.toString());
        mockMvc.perform(get("/api/v1/leads").param("listingId", otherListing.id().toString()).header("Authorization", bearer))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"));
    }

    @Test
    void effectiveRoleFollowsPriorityWhenAUserHasSeveralRoleRows() throws Exception {
        assertEffectiveRole(List.of(Roles.USER, Roles.OWNER), Roles.OWNER);
        assertEffectiveRole(List.of(Roles.OWNER, Roles.BROKER), Roles.BROKER);
        assertEffectiveRole(List.of(Roles.BROKER, Roles.MODERATOR), Roles.MODERATOR);
        assertEffectiveRole(List.of(Roles.USER, Roles.ADMIN, Roles.OWNER), Roles.ADMIN);
        assertThat(Roles.highest(List.of(Roles.USER, Roles.OWNER))).isEqualTo(Roles.OWNER);
        assertThat(Roles.highest(List.of())).isEqualTo(Roles.USER);
    }

    @Test
    void registrationRejectsStaffAccountTypes() throws Exception {
        for (String accountType : List.of("ADMIN", "MODERATOR", "owner")) {
            mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email":"reject-%s@example.test","password":"%s","name":"Người dùng","accountType":"%s"}
                            """.formatted(UUID.randomUUID(), TestData.DEFAULT_PASSWORD, accountType)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void adminUserListFiltersOwnersAndListsEachUserOnce() throws Exception {
        TestData.TestUser owner = data.user().role(Roles.OWNER).name("Chủ nhà lọc vai trò").create();
        jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, 'USER')", owner.id());

        JsonNode page = json.readTree(mockMvc.perform(get("/api/v1/admin/users").param("role", "OWNER").param("size", "100")
                        .param("query", "Chủ nhà lọc vai trò")
                        .with(user(UUID.randomUUID().toString()).roles(Roles.ADMIN)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(page.get("items").findValuesAsText("id")).containsOnlyOnce(owner.id().toString());
        assertThat(page.get("items").findValuesAsText("role")).containsOnly(Roles.OWNER);
        assertThat(page.get("total").asLong()).isEqualTo(page.get("items").size());
        mockMvc.perform(get("/api/v1/admin/users").param("role", "SUPERUSER").with(user(UUID.randomUUID().toString()).roles(Roles.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    private void assertEffectiveRole(List<String> rows, String expected) throws Exception {
        TestData.TestUser account = data.user().role(rows.get(0)).create();
        for (String extra : rows.subList(1, rows.size())) {
            jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, ?)", account.id(), extra);
        }
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + data.sessionFor(account.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(expected));
    }
}
