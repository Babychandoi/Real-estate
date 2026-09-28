package com.company.bds.analytics.application;

import java.util.List;

/** A rejected event batch; nothing of the batch is stored. The api layer maps it to Problem Details. */
public class InvalidEventsException extends RuntimeException {
    private final transient List<EventViolation> violations;

    public InvalidEventsException(List<EventViolation> violations) {
        super("Lô sự kiện có " + violations.size() + " lỗi.");
        this.violations = List.copyOf(violations);
    }

    public List<EventViolation> violations() { return violations; }
}
