package com.company.bds.shared.error;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * F22.3 through the whole stack (filters, Spring Security, MVC, advices): every error of the API — security,
 * validation, framework and business — is RFC 9457 Problem Details with the same members, media type and a
 * {@code traceId} equal to the {@code X-Request-Id} of the response. None of these may be a 500.
 */
@BdsIntegrationTest
class ProblemDetailsContractTests {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired TestData data;

    @Test
    void unauthenticatedAndForbiddenRequests() throws Exception {
        assertProblem(get("/api/v1/me/saved-listings"), 401, "UNAUTHORIZED");
        String user = bearer("USER");
        assertProblem(get("/api/v1/moderation/queue").header("Authorization", user), 403, "FORBIDDEN");
    }

    @Test
    void bodyValidationListsTheFields() throws Exception {
        JsonNode problem = assertProblem(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"),
                400, "VALIDATION_ERROR");
        assertThat(problem.path("errors").isArray()).isTrue();
        assertThat(problem.path("errors")).isNotEmpty();
        for (JsonNode error : problem.path("errors")) {
            assertThat(error.path("field").asText()).isNotBlank();
            assertThat(error.path("message").asText()).isNotBlank();
        }
    }

    @Test
    void unreadableBodyAndWrongMediaType() throws Exception {
        assertProblem(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"),
                400, "VALIDATION_ERROR");
        assertProblem(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("email=a"),
                415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void mistypedPathVariableNamesTheParameter() throws Exception {
        JsonNode problem = assertProblem(get("/api/v1/listings/not-a-uuid"), 400, "VALIDATION_ERROR");
        assertThat(problem.path("errors").get(0).path("field").asText()).isEqualTo("id");
        // the rejected value is not echoed back (it may be anything the client typed)
        assertThat(problem.path("detail").asText() + problem.path("errors")).doesNotContain("not-a-uuid");
    }

    @Test
    void unknownRouteAndWrongMethod() throws Exception {
        String user = bearer("USER");
        assertProblem(get("/api/v1/me/s9-no-such-route").header("Authorization", user), 404, "NOT_FOUND");
        MvcResult wrongMethod = perform(delete("/api/v1/auth/login").header("Authorization", user));
        assertProblemShape(wrongMethod, 405, "METHOD_NOT_ALLOWED");
        assertThat(wrongMethod.getResponse().getHeader("Allow")).contains("POST");
    }

    @Test
    void missingResourcesAnswerProblemDetailsNotAnEmptyBody() throws Exception {
        assertProblem(get("/api/v1/listings/" + UUID.randomUUID()), 404, "LISTING_NOT_FOUND");
        assertProblem(get("/api/v1/public/profiles/" + UUID.randomUUID()), 404, "PROFILE_NOT_FOUND");
    }

    @Test
    void searchAdviceUsesTheSameMembers() throws Exception {
        JsonNode problem = assertProblem(get("/api/v2/listings/search").param("purpose", "BUY"), 400, "INVALID_FILTER");
        assertThat(problem.path("errors").get(0).path("param").asText()).isEqualTo("purpose");
    }

    private String bearer(String role) {
        return "Bearer " + data.sessionFor(data.user().role(role).create().id());
    }

    private JsonNode assertProblem(RequestBuilder request, int status, String code) throws Exception {
        return assertProblemShape(perform(request), status, code);
    }

    private MvcResult perform(RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
    }

    static JsonNode assertProblemShape(MvcResult result, int status, String code) throws Exception {
        var response = result.getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = JSON.readTree(response.getContentAsByteArray());
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("type").asText()).isEqualTo(ProblemDetails.typeOf(code).toString());
        assertThat(body.path("title").asText()).isNotBlank();
        assertThat(body.path("detail").asText()).isNotBlank();
        assertThat(body.path("instance").asText()).startsWith("/");
        assertThat(body.path("traceId").asText()).isEqualTo(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(body.toString()).doesNotContain("Exception", "at com.company");
        return body;
    }
}
