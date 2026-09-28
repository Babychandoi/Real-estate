package com.company.bds.iam;

import com.company.bds.iam.application.AuthService;
import com.company.bds.shared.security.Roles;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Logout, revocation and expiry of bearer sessions (F20.2, ADR 0001 test plan). */
@BdsIntegrationTest
class SessionLifecycleTests {
    private static final String CHROME = StaffLogin.USER_AGENT;
    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    StaffLogin staff;

    @BeforeEach
    void setUp() { staff = new StaffLogin(mockMvc, json, jdbc); }

    private JsonNode login(TestData.TestUser user, String userAgent, String ip) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/v1/auth/login").header("User-Agent", userAgent)
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user.email(), user.password())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String bearer(TestData.TestUser user) throws Exception {
        return "Bearer " + login(user, CHROME, "203.0.113.10").get("accessToken").asText();
    }

    private void expectSignedIn(String bearer, boolean signedIn) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer))
                .andExpect(signedIn ? status().isOk() : status().isUnauthorized());
    }

    @Test
    void logoutRevokesOnlyThatTokenAndTheTokenStaysDead() throws Exception {
        TestData.TestUser user = data.user().create();
        String first = bearer(user);
        String second = bearer(user);
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", first)).andExpect(status().isNoContent());
        expectSignedIn(first, false);
        expectSignedIn(second, true);
        assertThat(jdbc.queryForObject("SELECT revoked_reason FROM auth_sessions WHERE token_hash = ?", String.class,
                AuthService.sha256(first.substring(7)))).isEqualTo("LOGOUT");
        // The dead token is simply unauthenticated now.
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", first)).andExpect(status().isUnauthorized());
    }

    @Test
    void absoluteExpiryAndLockedAccountsEndTheSessionAtTheNextRequest() throws Exception {
        TestData.TestUser user = data.user().create();
        JsonNode login = login(user, CHROME, "203.0.113.10");
        Instant expiresAt = Instant.parse(login.get("expiresAt").asText());
        assertThat(Duration.between(Instant.now(), expiresAt)).isCloseTo(Duration.ofHours(12), Duration.ofMinutes(1));
        assertThat(login.get("idleExpiresAt").isNull()).as("members have no idle timeout").isTrue();
        String bearer = "Bearer " + login.get("accessToken").asText();
        expectSignedIn(bearer, true);
        jdbc.update("UPDATE auth_sessions SET expires_at = now() - interval '1 second' WHERE token_hash = ?",
                AuthService.sha256(bearer.substring(7)));
        expectSignedIn(bearer, false);

        String other = bearer(user);
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", user.id());
        expectSignedIn(other, false);
    }

    @Test
    void staffSessionsLastEightHoursAndEndAfterThirtyIdleMinutesWhileMembersDoNotIdleOut() throws Exception {
        TestData.TestUser moderator = data.user().role(Roles.MODERATOR).create();
        StaffLogin.Enrolled enrolled = staff.enrol(moderator.email());
        String staffBearer = "Bearer " + enrolled.accessToken();
        Instant staffExpiry = jdbc.queryForObject("SELECT expires_at FROM auth_sessions WHERE token_hash = ?", java.sql.Timestamp.class,
                AuthService.sha256(enrolled.accessToken())).toInstant();
        assertThat(Duration.between(Instant.now(), staffExpiry)).isCloseTo(Duration.ofHours(8), Duration.ofMinutes(1));
        assertThat(jdbc.queryForObject("SELECT idle_timeout_seconds FROM auth_sessions WHERE token_hash = ?", Integer.class,
                AuthService.sha256(enrolled.accessToken()))).isEqualTo(1800);

        // 29 idle minutes: still valid, and the request records activity.
        jdbc.update("UPDATE auth_sessions SET last_seen_at = now() - interval '29 minutes' WHERE token_hash = ?",
                AuthService.sha256(enrolled.accessToken()));
        expectSignedIn(staffBearer, true);
        Instant touched = jdbc.queryForObject("SELECT last_seen_at FROM auth_sessions WHERE token_hash = ?", java.sql.Timestamp.class,
                AuthService.sha256(enrolled.accessToken())).toInstant();
        assertThat(touched).isCloseTo(Instant.now(), within(Duration.ofSeconds(30)));
        // 31 idle minutes: gone.
        jdbc.update("UPDATE auth_sessions SET last_seen_at = now() - interval '31 minutes' WHERE token_hash = ?",
                AuthService.sha256(enrolled.accessToken()));
        expectSignedIn(staffBearer, false);

        TestData.TestUser member = data.user().create();
        String memberBearer = bearer(member);
        jdbc.update("UPDATE auth_sessions SET last_seen_at = now() - interval '6 hours' WHERE token_hash = ?",
                AuthService.sha256(memberBearer.substring(7)));
        expectSignedIn(memberBearer, true);
    }

    @Test
    void theNotificationStreamDoesNotKeepAnIdleStaffSessionAlive() throws Exception {
        TestData.TestUser moderator = data.user().role(Roles.MODERATOR).create();
        String token = staff.enrol(moderator.email()).accessToken();
        String hash = AuthService.sha256(token);
        jdbc.update("UPDATE auth_sessions SET last_seen_at = now() - interval '20 minutes' WHERE token_hash = ?", hash);
        Instant before = jdbc.queryForObject("SELECT last_seen_at FROM auth_sessions WHERE token_hash = ?", java.sql.Timestamp.class, hash).toInstant();
        // The stream request is authenticated (whatever its outcome) but records no activity.
        mockMvc.perform(get("/api/v1/notifications/stream").header("Authorization", "Bearer " + token)
                .header("Accept", "text/event-stream")).andReturn();
        assertThat(jdbc.queryForObject("SELECT last_seen_at FROM auth_sessions WHERE token_hash = ?", java.sql.Timestamp.class, hash)
                .toInstant()).isEqualTo(before);
        // A real request does.
        expectSignedIn("Bearer " + token, true);
        assertThat(jdbc.queryForObject("SELECT last_seen_at FROM auth_sessions WHERE token_hash = ?", java.sql.Timestamp.class, hash)
                .toInstant()).isAfter(before);
    }

    @Test
    void activityIsRecordedAtMostOnceAMinute() throws Exception {
        TestData.TestUser user = data.user().create();
        String bearer = bearer(user);
        String hash = AuthService.sha256(bearer.substring(7));
        jdbc.update("UPDATE auth_sessions SET last_seen_at = now() - interval '20 seconds' WHERE token_hash = ?", hash);
        Instant before = jdbc.queryForObject("SELECT last_seen_at FROM auth_sessions WHERE token_hash = ?", java.sql.Timestamp.class, hash).toInstant();
        expectSignedIn(bearer, true);
        assertThat(jdbc.queryForObject("SELECT last_seen_at FROM auth_sessions WHERE token_hash = ?", java.sql.Timestamp.class, hash)
                .toInstant()).isEqualTo(before);
    }

    @Test
    void theAccountListsItsSessionsAndRevokesOneOrAllOthers() throws Exception {
        TestData.TestUser user = data.user().create();
        String laptop = "Bearer " + login(user, CHROME, "203.0.113.10").get("accessToken").asText();
        String phone = "Bearer " + login(user, IPHONE, "198.51.100.77").get("accessToken").asText();
        String tablet = bearer(user);
        TestData.TestUser stranger = data.user().create();
        String strangerBearer = bearer(stranger);

        JsonNode list = json.readTree(mockMvc.perform(get("/api/v1/me/sessions").header("Authorization", laptop))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString());
        assertThat(list).hasSize(3);
        JsonNode current = null;
        JsonNode phoneRow = null;
        for (JsonNode row : list) {
            if (row.get("current").asBoolean()) current = row;
            if ("Safari trên iOS".equals(row.get("device").asText())) phoneRow = row;
        }
        assertThat(current).isNotNull();
        assertThat(current.get("device").asText()).isEqualTo("Chrome trên Windows");
        assertThat(current.get("ipHint").asText()).isEqualTo("203.0.113.x");
        assertThat(phoneRow).isNotNull();
        assertThat(phoneRow.get("ipHint").asText()).isEqualTo("198.51.100.x");
        assertThat(list.toString()).doesNotContain("198.51.100.77").doesNotContain("token");

        // Someone else's session id: 404, and it keeps working.
        String strangerSession = jdbc.queryForObject("SELECT id::text FROM auth_sessions WHERE user_id = ?", String.class, stranger.id());
        mockMvc.perform(delete("/api/v1/me/sessions/" + strangerSession).header("Authorization", laptop)).andExpect(status().isNotFound());
        expectSignedIn(strangerBearer, true);

        mockMvc.perform(delete("/api/v1/me/sessions/" + phoneRow.get("id").asText()).header("Authorization", laptop))
                .andExpect(status().isNoContent());
        expectSignedIn(phone, false);
        expectSignedIn(tablet, true);
        expectSignedIn(laptop, true);

        mockMvc.perform(post("/api/v1/me/sessions/revoke-others").header("Authorization", laptop))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revokedSessions").value(1));
        expectSignedIn(tablet, false);
        expectSignedIn(laptop, true);
        mockMvc.perform(get("/api/v1/me/security-events").header("Authorization", laptop))
                .andExpect(jsonPath("$[0].type").value("OTHER_SESSIONS_REVOKED"))
                .andExpect(jsonPath("$[1].type").value("SESSION_REVOKED"));
    }

    @Test
    void passwordChangeNeedsTheCurrentPasswordKeepsThisDeviceAndEndsTheOthers() throws Exception {
        TestData.TestUser user = data.user().create();
        String here = bearer(user);
        String elsewhere = bearer(user);
        mockMvc.perform(post("/api/v1/me/password").header("Authorization", here).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong-Password-1\",\"newPassword\":\"New-Strong-Password-2026\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        expectSignedIn(elsewhere, true);
        mockMvc.perform(post("/api/v1/me/password").header("Authorization", here).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(user.password(), user.password())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
        mockMvc.perform(post("/api/v1/me/password").header("Authorization", here).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"New-Strong-Password-2026\"}".formatted(user.password())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revokedSessions").value(1));
        expectSignedIn(here, true);
        expectSignedIn(elsewhere, false);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user.email(), user.password())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"New-Strong-Password-2026\"}".formatted(user.email())))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM background_jobs WHERE dedupe_key LIKE ?", Integer.class,
                "password-changed:" + user.id() + ":%")).as("the account is told by e-mail").isEqualTo(1);
    }

    @Test
    void passwordResetRevokesEverySessionOfTheAccount() throws Exception {
        TestData.TestUser user = data.user().create();
        String one = bearer(user);
        String two = bearer(user);
        String raw = "reset-" + UUID.randomUUID() + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO password_reset_tokens(id,user_id,token_hash,expires_at) VALUES (?,?,?,now() + interval '10 minutes')",
                UUID.randomUUID(), user.id(), AuthService.sha256(raw));
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"Another-Strong-Password-9\"}".formatted(raw)))
                .andExpect(status().isNoContent());
        expectSignedIn(one, false);
        expectSignedIn(two, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id = ? AND revoked_reason = 'PASSWORD_RESET'",
                Integer.class, user.id())).isEqualTo(2);
    }

    @Test
    void roleChangeTakesEffectAtOnceAndSignsTheAccountOut() throws Exception {
        TestData.TestUser admin = data.user().role(Roles.ADMIN).create();
        TestData.TestUser broker = data.user().role(Roles.BROKER).create();
        String adminBearer = "Bearer " + staff.enrol(admin.email()).accessToken();
        String brokerBearer = bearer(broker);
        mockMvc.perform(get("/api/v1/broker/workspace").header("Authorization", brokerBearer)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/users/" + broker.id() + "/role").header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MODERATOR\",\"reason\":\"Chuyển sang đội kiểm duyệt\"}"))
                .andExpect(status().isOk());
        expectSignedIn(brokerBearer, false);
        // The promoted account now goes through the staff portal and must enrol a second factor.
        assertThat(staff.passwordStep(broker.email()).get("mfaState").asText()).isEqualTo("ENROLL");

        // Admin "sign out everywhere" for another account, with a reason.
        TestData.TestUser member = data.user().create();
        String memberBearer = bearer(member);
        mockMvc.perform(post("/api/v1/admin/users/" + member.id() + "/sessions/revoke").header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Nghi lộ mật khẩu\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revokedSessions").value(1));
        expectSignedIn(memberBearer, false);
        mockMvc.perform(get("/api/v1/admin/users/" + member.id() + "/history").header("Authorization", adminBearer))
                .andExpect(jsonPath("$[0].action").value("SESSIONS_REVOKE"))
                .andExpect(jsonPath("$[0].reason").value("Nghi lộ mật khẩu"));
    }

    @Test
    void emailVerificationDropsSessionsCreatedBeforeIt() throws Exception {
        TestData.TestUser pending = data.user().status("PENDING_EMAIL_VERIFICATION").create();
        String stale = "Bearer " + data.sessionFor(pending.id());
        String raw = "verify-" + UUID.randomUUID() + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO email_verification_tokens(id,user_id,token_hash,expires_at) VALUES (?,?,?,now() + interval '1 hour')",
                UUID.randomUUID(), pending.id(), AuthService.sha256(raw));
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED"));
        expectSignedIn(stale, false);
        expectSignedIn(bearer(pending), true);
    }
}
