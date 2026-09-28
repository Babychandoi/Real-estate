package com.company.bds.iam;

import com.company.bds.iam.application.AuthService;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Single-use, expiring links behind /verify-email and /reset-password (UI-14) and the return path (DS-11). */
@BdsIntegrationTest
class AuthTokenPagesTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    private static String rawToken() { return "tok-" + UUID.randomUUID() + UUID.randomUUID(); }

    private String verificationToken(UUID userId, String interval, String returnPath) {
        String raw = rawToken();
        jdbc.update("INSERT INTO email_verification_tokens(id,user_id,token_hash,expires_at,return_path) VALUES (?,?,?,now() + CAST(? AS interval),?)",
                UUID.randomUUID(), userId, AuthService.sha256(raw), interval, returnPath);
        return raw;
    }

    private String resetToken(UUID userId, String interval) {
        String raw = rawToken();
        jdbc.update("INSERT INTO password_reset_tokens(id,user_id,token_hash,expires_at) VALUES (?,?,?,now() + CAST(? AS interval))",
                UUID.randomUUID(), userId, AuthService.sha256(raw), interval);
        return raw;
    }

    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions resetStatus(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    @Test
    void verificationLinksAreSingleUseAndTellEveryFailureApart() throws Exception {
        TestData.TestUser pending = data.user().status("PENDING_EMAIL_VERIFICATION").create();
        String token = verificationToken(pending.id(), "1 hour", "/search?purpose=RENT");
        verify(token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.returnTo").value("/search?purpose=RENT"));
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, pending.id())).isEqualTo("ACTIVE");
        // Opened again (second tab, mail scanner): no error, just "already verified".
        verify(token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ALREADY_VERIFIED"));

        TestData.TestUser late = data.user().status("PENDING_EMAIL_VERIFICATION").create();
        verify(verificationToken(late.id(), "-1 minute", null)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        verify(rawToken()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, late.id())).isEqualTo("PENDING_EMAIL_VERIFICATION");

        // A used link of an account that is not active (locked since) is reported as used, not as success.
        TestData.TestUser locked = data.user().status("PENDING_EMAIL_VERIFICATION").create();
        String lockedToken = verificationToken(locked.id(), "1 hour", null);
        verify(lockedToken).andExpect(status().isOk());
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", locked.id());
        verify(lockedToken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOKEN_USED"));

        // The old GET link still works for e-mails sent before this release.
        TestData.TestUser legacy = data.user().status("PENDING_EMAIL_VERIFICATION").create();
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", verificationToken(legacy.id(), "1 hour", null)))
                .andExpect(status().isNoContent());
    }

    @Test
    void resendingReplacesTheOlderLinkAndKeepsTheReturnPath() throws Exception {
        TestData.TestUser pending = data.user().status("PENDING_EMAIL_VERIFICATION").create();
        String first = verificationToken(pending.id(), "1 hour", "/listings/can-ho-cau-giay");
        mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + pending.email() + "\"}")).andExpect(status().isAccepted());
        verify(first).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOKEN_SUPERSEDED"));
        assertThat(jdbc.queryForList("SELECT return_path FROM email_verification_tokens WHERE user_id = ? AND superseded_at IS NULL",
                String.class, pending.id())).containsExactly("/listings/can-ho-cau-giay");
        // Unknown addresses get the same neutral answer.
        mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.test\"}")).andExpect(status().isAccepted());
    }

    @Test
    void registrationStoresOnlyASafeReturnPath() throws Exception {
        String[][] cases = {
                {"/search?purpose=SALE&district=005", "/search?purpose=SALE&district=005"},
                {"https://evil.example/phish", null},
                {"//evil.example", null},
                {"/2026/nhadatchuan/admin/users", null}};
        for (String[] c : cases) {
            String email = "ret-" + UUID.randomUUID() + "@example.test";
            mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email":"%s","password":"%s","name":"Người thử quay lại","returnTo":"%s"}
                            """.formatted(email, TestData.DEFAULT_PASSWORD, c[0])))
                    .andExpect(status().isCreated());
            assertThat(jdbc.queryForObject("""
                    SELECT t.return_path FROM email_verification_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = ?
                    """, String.class, email)).as(c[0]).isEqualTo(c[1]);
        }
    }

    @Test
    void resetLinksReportTheirStateWithoutBeingConsumedAndWorkExactlyOnce() throws Exception {
        TestData.TestUser user = data.user().create();
        String token = resetToken(user.id(), "20 minutes");
        resetStatus(token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        resetStatus(token).andExpect(jsonPath("$.status").value("VALID"));
        resetStatus(rawToken()).andExpect(jsonPath("$.status").value("INVALID"));
        resetStatus(resetToken(user.id(), "-1 second")).andExpect(jsonPath("$.status").value("EXPIRED"));

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"Brand-New-Password-77\"}".formatted(token)))
                .andExpect(status().isNoContent());
        resetStatus(token).andExpect(jsonPath("$.status").value("USED"));
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"Other-New-Password-78\"}".formatted(token)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOKEN_USED"));
        assertThat(jdbc.queryForList("SELECT event_type FROM auth_security_events WHERE user_id = ?", String.class, user.id()))
                .contains("PASSWORD_RESET");
    }

    @Test
    void aNewerResetRequestSupersedesTheOlderLink() throws Exception {
        TestData.TestUser user = data.user().create();
        String older = resetToken(user.id(), "20 minutes");
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.email() + "\"}")).andExpect(status().isAccepted());
        resetStatus(older).andExpect(jsonPath("$.status").value("SUPERSEDED"));
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"Brand-New-Password-77\"}".formatted(older)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOKEN_SUPERSEDED"));
    }
}
