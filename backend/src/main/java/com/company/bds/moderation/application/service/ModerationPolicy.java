package com.company.bds.moderation.application.service;

import java.time.Duration;

/** Moderation service levels (documented in docs/audit-2026-09-27/streams/s4-admin.md). */
public final class ModerationPolicy {
    /** A submission waiting longer than this breaches the SLA. */
    public static final Duration SLA = Duration.ofHours(24);
    /** A claim protects an item from other moderators for this long; renewing it restarts the period. */
    public static final Duration CLAIM_TTL = Duration.ofMinutes(30);
    /** Bulk actions touch at most this many items, all under the actor's own claims. */
    public static final int BULK_MAX = 50;
    /** Share of the previous week's approvals re-checked by the random audit (at least one, at most AUDIT_MAX). */
    public static final double AUDIT_RATE = 0.05;
    public static final int AUDIT_MAX = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private ModerationPolicy() {}
}
