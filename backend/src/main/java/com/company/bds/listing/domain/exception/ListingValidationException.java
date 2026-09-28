package com.company.bds.listing.domain.exception;

import java.util.List;

/** Field-level validation failure of a listing draft; mapped to 400 Problem Details with {@code errors[{field,message}]}. */
public class ListingValidationException extends RuntimeException {
    public record FieldIssue(String field, String message) {}

    private final List<FieldIssue> issues;

    public ListingValidationException(List<FieldIssue> issues) {
        super("Có " + issues.size() + " trường dữ liệu cần kiểm tra lại.");
        this.issues = List.copyOf(issues);
    }

    public List<FieldIssue> issues() { return issues; }
}
