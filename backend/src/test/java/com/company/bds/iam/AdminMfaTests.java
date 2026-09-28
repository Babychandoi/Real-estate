package com.company.bds.iam;

import com.company.bds.iam.domain.Totp;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff second factor (F20.3, UI-17): enrolment, verification, replay, recovery codes, brute force, admin reset. */
@BdsIntegrationTest
class AdminMfaTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    StaffLogin staff;

    @BeforeEach
    void setUp() { staff = new StaffLogin(mockMvc, json, jdbc); }

    @Test
    void staffWithoutAuthenticatorMustEnrolAndTheChallengeIsNeverASession() throws Exception {
        TestData.TestUser moderator = data.user().role(Roles.MODERATOR).create();
        JsonNode first = staff.passwordStep(moderator.email());
        assertThat(first.get("mfaRequired").asBoolean()).isTrue();
        assertThat(first.get("mfaState").asText()).isEqualTo("ENROLL");
        assertThat(first.get("accessToken").isNull()).isTrue();
        String challenge = first.get("challengeToken").asText();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id = ?", Integer.class, moderator.id())).isZero();
        // A challenge token is not a bearer token.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + challenge)).andExpect(status().isUnauthorized());
        // Confirming before a secret exists, or verifying an ENROLL challenge, is refused.
        staff.call("/api/v1/auth/admin/mfa/enroll/confirm", "{\"challengeToken\":\"%s\",\"code\":\"123456\"}".formatted(challenge))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MFA_ENROLLMENT_NOT_STARTED"));
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"123456\"}".formatted(challenge))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"));

        JsonNode enrollment = staff.body(staff.call("/api/v1/auth/admin/mfa/enroll", "{\"challengeToken\":\"" + challenge + "\"}")
                .andExpect(status().isOk()));
        String secret = enrollment.get("secret").asText();
        assertThat(enrollment.get("otpauthUri").asText()).startsWith("otpauth://totp/").contains("secret=" + secret);
        // The secret is stored sealed, never in clear.
        assertThat(jdbc.queryForObject("SELECT pending_secret_sealed FROM mfa_challenges WHERE user_id = ? AND consumed_at IS NULL",
                String.class, moderator.id())).startsWith("s1:").doesNotContain(secret);

        staff.call("/api/v1/auth/admin/mfa/enroll/confirm", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(challenge,
                        wrong(Totp.code(secret, Totp.step(Instant.now())))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        JsonNode done = staff.body(staff.call("/api/v1/auth/admin/mfa/enroll/confirm", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}"
                .formatted(challenge, Totp.code(secret, Totp.step(Instant.now())))).andExpect(status().isOk()));
        assertThat(done.get("recoveryCodes")).hasSize(10);
        assertThat(done.get("recoveryCodes").get(0).asText()).matches("[A-Z2-9]{5}-[A-Z2-9]{5}");
        String bearer = "Bearer " + done.get("session").get("accessToken").asText();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(Roles.MODERATOR));
        assertThat(jdbc.queryForObject("SELECT secret_sealed FROM user_mfa WHERE user_id = ?", String.class, moderator.id()))
                .startsWith("s1:").doesNotContain(secret);
        assertThat(jdbc.queryForObject("SELECT mfa_verified_at IS NOT NULL FROM auth_sessions WHERE user_id = ?", Boolean.class,
                moderator.id())).isTrue();
        // The challenge was single use.
        staff.call("/api/v1/auth/admin/mfa/enroll", "{\"challengeToken\":\"" + challenge + "\"}").andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/me/mfa").header("Authorization", bearer))
                .andExpect(jsonPath("$.enrolled").value(true)).andExpect(jsonPath("$.recoveryCodesRemaining").value(10))
                .andExpect(jsonPath("$.required").value(true));
        mockMvc.perform(get("/api/v1/me/security-events").header("Authorization", bearer))
                .andExpect(jsonPath("$[0].type").value("MFA_ENROLLED"))
                .andExpect(jsonPath("$[0].device").value("Chrome trên Windows"));
    }

    @Test
    void enrolledStaffNeedsAFreshCodeAndACodeIsNeverAcceptedTwice() throws Exception {
        TestData.TestUser admin = data.user().role(Roles.ADMIN).create();
        StaffLogin.Enrolled enrolled = staff.enrol(admin.email());
        long enrolledStep = jdbc.queryForObject("SELECT last_used_step FROM user_mfa WHERE user_id = ?", Long.class, admin.id());

        JsonNode challenge = staff.passwordStep(admin.email());
        assertThat(challenge.get("mfaState").asText()).isEqualTo("VERIFY");
        String token = challenge.get("challengeToken").asText();
        // The code used for enrolment cannot be replayed to sign in.
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}"
                        .formatted(token, Totp.code(enrolled.secret(), enrolledStep)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        String next = Totp.code(enrolled.secret(), enrolledStep + 1);
        JsonNode session = staff.body(staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}"
                .formatted(token, next)).andExpect(status().isOk()));
        assertThat(session.get("user").get("role").asText()).isEqualTo(Roles.ADMIN);
        assertThat(session.get("idleExpiresAt").isNull()).isFalse();

        // Same code on a new challenge: refused (RFC 6238 §5.2).
        String again = staff.passwordStep(admin.email()).get("challengeToken").asText();
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(again, next))
                .andExpect(status().isBadRequest());
        // Both a code and a recovery code, or neither: 400.
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\"}".formatted(again)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MFA_CODE_REQUIRED"));
    }

    @Test
    void recoveryCodesWorkOnceAndCanBeRegeneratedOnlyWithAValidCode() throws Exception {
        TestData.TestUser admin = data.user().role(Roles.ADMIN).create();
        StaffLogin.Enrolled enrolled = staff.enrol(admin.email());
        String recovery = enrolled.recoveryCodes().get(3);

        String token = staff.passwordStep(admin.email()).get("challengeToken").asText();
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"recoveryCode\":\"%s\"}"
                .formatted(token, recovery.toLowerCase().replace("-", " "))).andExpect(status().isOk());
        String second = staff.passwordStep(admin.email()).get("challengeToken").asText();
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"recoveryCode\":\"%s\"}".formatted(second, recovery))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_mfa_recovery_codes WHERE user_id = ? AND used_at IS NULL",
                Integer.class, admin.id())).isEqualTo(9);

        String bearer = "Bearer " + enrolled.accessToken();
        mockMvc.perform(post("/api/v1/me/mfa/recovery-codes").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"000000\"}")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE user_mfa SET last_used_step = ? WHERE user_id = ?", Totp.step(Instant.now()) - 2, admin.id());
        JsonNode fresh = json.readTree(mockMvc.perform(post("/api/v1/me/mfa/recovery-codes").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(Totp.code(enrolled.secret(), Totp.step(Instant.now())))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(fresh.get("recoveryCodes")).hasSize(10);
        // Old codes stop working.
        String third = staff.passwordStep(admin.email()).get("challengeToken").asText();
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"recoveryCode\":\"%s\"}"
                .formatted(third, enrolled.recoveryCodes().get(0))).andExpect(status().isBadRequest());
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"recoveryCode\":\"%s\"}"
                .formatted(third, fresh.get("recoveryCodes").get(0).asText())).andExpect(status().isOk());
        List<String> types = jdbc.queryForList("SELECT event_type FROM auth_security_events WHERE user_id = ?", String.class, admin.id());
        assertThat(types).contains("MFA_RECOVERY_CODE_USED", "MFA_RECOVERY_CODES_REGENERATED", "MFA_FAILED");
    }

    @Test
    void fiveWrongCodesBurnTheChallengeAndExpiredChallengesAreRefused() throws Exception {
        TestData.TestUser admin = data.user().role(Roles.ADMIN).create();
        StaffLogin.Enrolled enrolled = staff.enrol(admin.email());
        String token = staff.passwordStep(admin.email()).get("challengeToken").asText();
        String right = Totp.code(enrolled.secret(), Totp.step(Instant.now()) + 1);
        for (int attempt = 1; attempt <= 4; attempt++) {
            staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(token, wrong(right)))
                    .andExpect(status().isBadRequest());
        }
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(token, wrong(right)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MFA_CHALLENGE_LOCKED"));
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(token, right))
                .andExpect(status().isUnauthorized());

        String expired = staff.passwordStep(admin.email()).get("challengeToken").asText();
        jdbc.update("UPDATE mfa_challenges SET expires_at = now() - interval '1 second' WHERE user_id = ? AND consumed_at IS NULL", admin.id());
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(expired, right))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"));

        // A newer login replaces an older open challenge.
        String older = staff.passwordStep(admin.email()).get("challengeToken").asText();
        String newer = staff.passwordStep(admin.email()).get("challengeToken").asText();
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(older, right))
                .andExpect(status().isUnauthorized());
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(newer, right))
                .andExpect(status().isOk());
    }

    @Test
    void wrongCodesAreCappedPerAccountAcrossChallengesAndRecoveryCodesAreStoredKeyed() throws Exception {
        TestData.TestUser admin = data.user().role(Roles.ADMIN).create();
        StaffLogin.Enrolled enrolled = staff.enrol(admin.email());
        String stored = jdbc.queryForList("SELECT code_hash FROM user_mfa_recovery_codes WHERE user_id = ?", String.class, admin.id()).get(0);
        assertThat(enrolled.recoveryCodes()).noneMatch(code -> com.company.bds.iam.application.AuthService.sha256(code.replace("-", "")).equals(stored)
                || com.company.bds.iam.application.AuthService.sha256(code).equals(stored));

        String right = Totp.code(enrolled.secret(), Totp.step(Instant.now()) + 1);
        // Two burnt challenges = 10 wrong codes within the hour.
        for (int challenge = 0; challenge < 2; challenge++) {
            String token = staff.passwordStep(admin.email()).get("challengeToken").asText();
            for (int attempt = 0; attempt < 5; attempt++) {
                staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(token, wrong(right)));
            }
        }
        staff.call("/api/v1/auth/admin/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(admin.email(), TestData.DEFAULT_PASSWORD))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("MFA_TEMPORARILY_LOCKED"));
        // An hour later the account can try again.
        jdbc.update("UPDATE mfa_challenges SET created_at = created_at - interval '61 minutes' WHERE user_id = ?", admin.id());
        String token = staff.passwordStep(admin.email()).get("challengeToken").asText();
        staff.call("/api/v1/auth/admin/mfa/verify", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(token, right))
                .andExpect(status().isOk());
    }

    @Test
    void anAdminResetsAnotherStaffMembersAuthenticatorWithAReasonAndTheirSessionsEnd() throws Exception {
        TestData.TestUser admin = data.user().role(Roles.ADMIN).create();
        TestData.TestUser moderator = data.user().role(Roles.MODERATOR).create();
        String adminBearer = "Bearer " + staff.enrol(admin.email()).accessToken();
        String moderatorBearer = "Bearer " + staff.enrol(moderator.email()).accessToken();

        mockMvc.perform(post("/api/v1/admin/users/" + moderator.id() + "/mfa/reset").header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/users/" + admin.id() + "/mfa/reset").header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Tự đặt lại\"}")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/users/" + admin.id() + "/mfa/reset").header("Authorization", moderatorBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Không có quyền\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users/" + moderator.id() + "/mfa/reset").header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Mất điện thoại, đã xác minh qua gọi video\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", moderatorBearer)).andExpect(status().isUnauthorized());
        assertThat(staff.passwordStep(moderator.email()).get("mfaState").asText()).isEqualTo("ENROLL");
        mockMvc.perform(get("/api/v1/admin/users/" + moderator.id() + "/history").header("Authorization", adminBearer))
                .andExpect(jsonPath("$[0].action").value("MFA_RESET"));
        mockMvc.perform(get("/api/v1/admin/users").param("query", moderator.email()).header("Authorization", adminBearer))
                .andExpect(jsonPath("$.items[0].mfaEnrolled").value(false));
        mockMvc.perform(get("/api/v1/admin/users").param("query", admin.email()).header("Authorization", adminBearer))
                .andExpect(jsonPath("$.items[0].mfaEnrolled").value(true));
    }

    @Test
    void theMemberPortalStillRefusesStaffAndTheStaffPortalStillRefusesMembers() throws Exception {
        TestData.TestUser member = data.user().role(Roles.BROKER).create();
        TestData.TestUser moderator = data.user().role(Roles.MODERATOR).create();
        mockMvc.perform(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.email(), TestData.DEFAULT_PASSWORD)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(moderator.email(), TestData.DEFAULT_PASSWORD)))
                .andExpect(status().isBadRequest());
        // Wrong password on the staff portal: no challenge, and the failure is kept in the account's history.
        mockMvc.perform(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Wrong-Password-2026!\"}".formatted(moderator.email())))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mfa_challenges WHERE user_id = ?", Integer.class, moderator.id())).isZero();
        assertThat(jdbc.queryForList("SELECT event_type FROM auth_security_events WHERE user_id = ?", String.class, moderator.id()))
                .contains("LOGIN_FAILED");
    }

    private static String wrong(String code) {
        return String.format("%06d", (Integer.parseInt(code) + 1) % 1_000_000);
    }
}
