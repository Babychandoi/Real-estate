package com.company.bds.lead.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum LeadStatus {
    NEW,
    CONTACTED,
    APPOINTED,
    CLOSED,
    SPAM,
    /** The requester withdrew the request; only the requester side may set it. */
    WITHDRAWN;

    /** Statuses in which the request is still being handled (the requester may withdraw, appointments may be made). */
    public static final Set<LeadStatus> OPEN = EnumSet.of(NEW, CONTACTED, APPOINTED);

    /**
     * Owner-side transitions: NEW → CONTACTED/APPOINTED/CLOSED/SPAM; CONTACTED ↔ APPOINTED, both → CLOSED/SPAM;
     * CLOSED/SPAM → CONTACTED (reopen). WITHDRAWN is terminal and nothing goes back to NEW.
     */
    public boolean ownerCanMoveTo(LeadStatus target) {
        if (target == this) return false;
        return switch (this) {
            case NEW -> target == CONTACTED || target == APPOINTED || target == CLOSED || target == SPAM;
            case CONTACTED -> target == APPOINTED || target == CLOSED || target == SPAM;
            case APPOINTED -> target == CONTACTED || target == CLOSED || target == SPAM;
            case CLOSED, SPAM -> target == CONTACTED;
            case WITHDRAWN -> false;
        };
    }
}
