package com.company.bds.lead.api.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request bodies of the lead commands; every write carries the version the client last read. */
public final class LeadCommandRequests {
    private LeadCommandRequests() {}

    public record Qualify(String qualification, String reason, String note, Long expectedVersion) {}

    public record Assign(UUID assigneeId, Long expectedVersion) {}

    public record Withdraw(String reason, Long expectedVersion) {}

    public record Slot(Instant startsAt, Instant endsAt) {}

    /** {@code replacesVersion}: version of the open appointment this proposal replaces (null when there is none). */
    public record Propose(List<Slot> slots, String note, Long replacesVersion) {}

    public record Confirm(UUID slotId, Long expectedVersion) {}

    public record Cancel(String reason, Long expectedVersion) {}

    public record Outcome(String outcome, String noShowParty, String note, Long expectedVersion) {}

    public record TeamMember(String email) {}
}
