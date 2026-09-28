package com.company.bds.search.domain;

import java.util.List;

/** A search request with invalid parameters (contract §7): answered with 400 and every error, never silently ignored. */
public class InvalidFilterException extends RuntimeException {
    public static final String INVALID_FILTER = "INVALID_FILTER";
    public static final String BBOX_TOO_LARGE = "BBOX_TOO_LARGE";

    public record FilterError(String param, String message) {}

    private final String code;
    private final transient List<FilterError> errors;

    public InvalidFilterException(String code, List<FilterError> errors) {
        super(code + ": " + errors.size() + " invalid parameter(s)");
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public String code() { return code; }

    public List<FilterError> errors() { return errors; }
}
