package com.company.bds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.any;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @SpyBean JavaMailSender mailSender;

    @BeforeEach
    void disableExternalEmailDelivery() { doNothing().when(mailSender).send(any(SimpleMailMessage.class)); }

    @Test
    void healthProbesArePublicButMetricsRemainProtected() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/public/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listingId":"71000000-0000-0000-0000-000000000006",
                                 "fullName":"Khach chua dang nhap","phone":"0900000002",
                                 "note":"Kiem tra xac thuc","consentPolicy":true}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticationAuthorizationAndRevocation() throws Exception {
        String listing = """
                {"purpose":"SALE","propertyType":"HOUSE","title":"Tin đăng kiểm thử bảo mật hợp lệ",
                 "priceVnd":2500000000,"areaM2":60,"description":"Dữ liệu kiểm thử tự động", "addressSummary":"Hà Nội"}
                """;
        mockMvc.perform(post("/api/v1/listings").contentType(MediaType.APPLICATION_JSON).content(listing))
                .andExpect(status().isUnauthorized());

        String email = "security-" + System.nanoTime() + "@example.test";
        String registration = """
                {"email":"%s","password":"Strong-Test-Password-2026!","name":"Người dùng kiểm thử","accountType":"USER"}
                """.formatted(email);
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Strong-Test-Password-2026!\"}".formatted(email)))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE users SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE email=?", email);
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Strong-Test-Password-2026!\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(body).get("accessToken").asText();

        String createdListing = mockMvc.perform(post("/api/v1/listings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(listing))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String listingId = mapper.readTree(createdListing).get("listingId").asText();
        java.util.UUID listingUuid = java.util.UUID.fromString(listingId);
        java.util.UUID revisionUuid = jdbc.queryForObject(
                "SELECT id FROM listing_revisions WHERE listing_id=? ORDER BY revision_number DESC LIMIT 1",
                java.util.UUID.class, listingUuid);
        jdbc.update("UPDATE listings SET status='ACTIVE', public_revision_id=? WHERE id=?",
                revisionUuid, listingUuid);

        mockMvc.perform(post("/api/v1/public/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listingId":"%s","fullName":"Tai khoan chua eKYC",
                                 "phone":"0900000003","note":"Kiem tra cong eKYC","consentPolicy":true}
                                """.formatted(listingId)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/moderation/queue").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/listings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(listing))
                .andExpect(status().isUnauthorized());
    }
}
