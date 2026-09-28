package com.company.bds.shared.security;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Audit F10.2: KYC, lead, billing, admin, auth and moderation responses are never stored by a shared cache.
 * Same context configuration as {@code SecurityIntegrationTests}, so the Spring context is reused.
 */
@BdsIntegrationTest
class SensitiveResponseCacheTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @SpyBean JavaMailSender mailSender;

    @BeforeEach
    void disableExternalEmailDelivery() { doNothing().when(mailSender).send(any(SimpleMailMessage.class)); }

    private static void assertNotStored(MvcResult result, String what) {
        String cacheControl = String.join(", ", result.getResponse().getHeaders("Cache-Control"));
        assertThat(cacheControl).as("Cache-Control of %s", what).contains("no-store").doesNotContain("public");
        assertThat(result.getResponse().getHeader("Pragma")).as("Pragma of %s", what).isEqualTo("no-cache");
    }

    @Test
    void loginResponseCarryingTheTokenAndSessionEndpointsAreNeverStored() throws Exception {
        String email = "cache-" + System.nanoTime() + "@example.test";
        String password = "Strong-Test-Password-2026!";
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"name\":\"Người dùng kiểm thử\",\"accountType\":\"USER\"}".formatted(email, password)))
                .andReturn();
        assertThat(registered.getResponse().getStatus()).isEqualTo(201);
        assertNotStored(registered, "register");
        jdbc.update("UPDATE users SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE email=?", email);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertNotStored(login, "login");
        String token = mapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asText();

        MvcResult me = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andReturn();
        assertThat(me.getResponse().getStatus()).isEqualTo(200);
        assertNotStored(me, "auth/me");

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)).andReturn();
        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        assertNotStored(logout, "logout");
    }

    @Test
    void kycLeadBillingAdminAndModerationResponsesAreNeverStored() throws Exception {
        record Probe(String path, String role) {}
        List<Probe> probes = List.of(
                new Probe("/api/v1/kyc/queue", "MODERATOR"),
                new Probe("/api/v1/leads", "BROKER"),
                new Probe("/api/v1/leads/sent", "USER"),
                new Probe("/api/v1/billing/orders", "BROKER"),
                new Probe("/api/v1/billing/admin/reconciliation", "ADMIN"),
                new Probe("/api/v1/admin/users", "ADMIN"),
                new Probe("/api/v1/moderation/queue", "MODERATOR"),
                new Probe("/api/v1/listings/my-listings", "BROKER"),
                new Probe("/api/v1/notifications", "USER"));
        for (Probe probe : probes) {
            MockHttpServletRequestBuilder request = get(probe.path()).with(user(UUID.randomUUID().toString()).roles(probe.role()));
            MvcResult result = mockMvc.perform(request).andReturn();
            // The handler ran (authorised). Its exact status depends on what data exists for this probe (e.g. no
            // bank settings row yet); the cache policy has to hold for every status, success or error.
            assertThat(result.getResponse().getStatus()).as(probe.path()).isNotIn(401, 403, 404);
            assertNotStored(result, probe.path());
        }
    }

    @Test
    void rejectedRequestsToPrivateAreasAreNotStoredEither() throws Exception {
        MvcResult anonymous = mockMvc.perform(get("/api/v1/kyc/queue")).andReturn();
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertNotStored(anonymous, "anonymous kyc queue");

        MvcResult forbidden = mockMvc.perform(get("/api/v1/admin/users").with(user(UUID.randomUUID().toString()).roles("USER"))).andReturn();
        assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);
        assertNotStored(forbidden, "admin users as USER");
    }
}
