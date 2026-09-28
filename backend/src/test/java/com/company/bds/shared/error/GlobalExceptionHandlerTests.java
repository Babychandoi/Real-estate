package com.company.bds.shared.error;

import com.company.bds.shared.observability.RequestIdFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * F22.3 for error sources no public endpoint triggers on demand: constraint violations, missing header/parameter,
 * upload limit, {@code ResponseStatusException}, {@code ApiException} (410/429), unexpected exceptions (500 without
 * the exception's message) and the {@code /error} dispatch.
 */
class GlobalExceptionHandlerTests {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    record Page(@Min(1) int page) {}

    @RestController
    static class FailingController {
        @GetMapping("/t/constraint")
        String constraint() {
            throw new ConstraintViolationException(VALIDATOR.validate(new Page(0)));
        }

        @GetMapping("/t/header")
        String header(@RequestHeader("Idempotency-Key") String key) { return key; }

        @GetMapping("/t/param")
        String param(@RequestParam int page) { return "" + page; }

        @GetMapping("/t/upload")
        String upload() { throw new MaxUploadSizeExceededException(10); }

        @GetMapping("/t/status")
        String status() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có."); }

        @GetMapping("/t/gone")
        String gone() { throw ApiException.gone("TOKEN_EXPIRED", "Liên kết đã hết hạn."); }

        @GetMapping("/t/quota")
        String quota() { throw ApiException.tooManyRequests("LEAD_QUOTA_EXCEEDED", "Hết lượt."); }

        @GetMapping("/t/boom")
        String boom() { throw new NullPointerException("secret internal state for user a@b.vn"); }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailingController(), new ProblemErrorController())
                .setControllerAdvice(new GlobalExceptionHandler(new StaticMessageSource()))
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void constraintViolationsAreFieldErrors() throws Exception {
        var problem = ProblemDetailsContractTests.assertProblemShape(mvc.perform(get("/t/constraint")).andReturn(), 400, "VALIDATION_ERROR");
        assertThat(problem.path("errors").get(0).path("field").asText()).isEqualTo("page");
        assertThat(problem.path("errors").get(0).path("code").asText()).isEqualTo("Min");
    }

    @Test
    void missingHeaderAndParameterAre400() throws Exception {
        var header = ProblemDetailsContractTests.assertProblemShape(mvc.perform(get("/t/header")).andReturn(), 400, "VALIDATION_ERROR");
        assertThat(header.path("errors").get(0).path("field").asText()).isEqualTo("Idempotency-Key");
        var param = ProblemDetailsContractTests.assertProblemShape(mvc.perform(get("/t/param")).andReturn(), 400, "VALIDATION_ERROR");
        assertThat(param.path("errors").get(0).path("field").asText()).isEqualTo("page");
        ProblemDetailsContractTests.assertProblemShape(mvc.perform(get("/t/param").param("page", "x")).andReturn(), 400, "VALIDATION_ERROR");
    }

    @Test
    void frameworkAndBusinessStatusesAreKept() throws Exception {
        ProblemDetailsContractTests.assertProblemShape(mvc.perform(get("/t/upload")).andReturn(), 413, "PAYLOAD_TOO_LARGE");
        var notFound = ProblemDetailsContractTests.assertProblemShape(mvc.perform(get("/t/status")).andReturn(), 404, "NOT_FOUND");
        assertThat(notFound.path("detail").asText()).isEqualTo("Không có.");
        ProblemDetailsContractTests.assertProblemShape(mvc.perform(get("/t/gone")).andReturn(), 410, "TOKEN_EXPIRED");
        ProblemDetailsContractTests.assertProblemShape(mvc.perform(get("/t/quota")).andReturn(), 429, "LEAD_QUOTA_EXCEEDED");
    }

    @Test
    void unexpectedExceptionsAre500WithoutTheirMessage() throws Exception {
        var result = mvc.perform(get("/t/boom")).andReturn();
        var problem = ProblemDetailsContractTests.assertProblemShape(result, 500, "INTERNAL_SERVER_ERROR");
        assertThat(problem.toString()).doesNotContain("secret", "a@b.vn", "NullPointer");
    }

    @Test
    void errorDispatchRendersProblemDetails() throws Exception {
        var result = mvc.perform(get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/anything")).andReturn();
        var problem = ProblemDetailsContractTests.assertProblemShape(result, 400, "BAD_REQUEST");
        assertThat(problem.path("instance").asText()).isEqualTo("/api/v1/anything");
        var failure = mvc.perform(get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("db password is x"))).andReturn();
        assertThat(ProblemDetailsContractTests.assertProblemShape(failure, 500, "INTERNAL_SERVER_ERROR").toString())
                .doesNotContain("password");
    }
}
