package com.company.bds.analytics.application;

import com.company.bds.shared.error.ProblemDetails;

import java.util.List;

/** A rejected event batch; nothing of the batch is stored. */
public class InvalidEventsException extends RuntimeException {
    private final transient List<ProblemDetails.ValidationErrorItem> errors;

    public InvalidEventsException(List<ProblemDetails.ValidationErrorItem> errors) {
        super("Lô sự kiện có " + errors.size() + " lỗi.");
        this.errors = List.copyOf(errors);
    }

    public List<ProblemDetails.ValidationErrorItem> errors() { return errors; }
}
