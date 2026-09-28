package com.company.bds.iam;

import com.company.bds.iam.domain.Totp;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Drives the staff portal through the real endpoints: password → challenge → enrolment or TOTP → session. */
final class StaffLogin {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/129.0 Safari/537.36";
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    StaffLogin(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
    }

    JsonNode passwordStep(String email) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/admin/login").header("User-Agent", USER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestData.DEFAULT_PASSWORD)))
                .andExpect(status().isOk()));
    }

    ResultActions call(String path, String body) throws Exception {
        return mvc.perform(post(path).header("User-Agent", USER_AGENT).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Enrols a staff account that has no authenticator yet; returns the secret, recovery codes and bearer. */
    Enrolled enrol(String email) throws Exception {
        JsonNode challenge = passwordStep(email);
        String token = challenge.get("challengeToken").asText();
        JsonNode enrollment = body(call("/api/v1/auth/admin/mfa/enroll", "{\"challengeToken\":\"" + token + "\"}")
                .andExpect(status().isOk()));
        String secret = enrollment.get("secret").asText();
        JsonNode done = body(call("/api/v1/auth/admin/mfa/enroll/confirm", """
                {"challengeToken":"%s","code":"%s"}""".formatted(token, Totp.code(secret, Totp.step(Instant.now()))))
                .andExpect(status().isOk()));
        List<String> codes = new ArrayList<>();
        done.get("recoveryCodes").forEach(code -> codes.add(code.asText()));
        return new Enrolled(secret, codes, done.get("session").get("accessToken").asText());
    }

    /** Signs an enrolled account in with a fresh TOTP code (the replay guard is moved back so tests can sign in often). */
    String signIn(String email, UUID userId, String secret) throws Exception {
        jdbc.update("UPDATE user_mfa SET last_used_step = ? WHERE user_id = ?", Totp.step(Instant.now()) - 2, userId);
        String token = passwordStep(email).get("challengeToken").asText();
        return body(call("/api/v1/auth/admin/mfa/verify", """
                {"challengeToken":"%s","code":"%s"}""".formatted(token, Totp.code(secret, Totp.step(Instant.now()))))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    JsonNode body(ResultActions result) throws Exception {
        String content = result.andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }

    record Enrolled(String secret, List<String> recoveryCodes, String accessToken) {}
}
