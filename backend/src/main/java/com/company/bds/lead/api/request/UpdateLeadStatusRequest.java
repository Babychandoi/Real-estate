package com.company.bds.lead.api.request;

import com.company.bds.lead.domain.model.LeadStatus;
import jakarta.validation.constraints.NotNull;

/** {@code expectedVersion} is the lead version the client last read (compare-and-set; stale → 409). */
public record UpdateLeadStatusRequest(
        @NotNull(message = "Trạng thái mới không được để trống")
        LeadStatus status,
        Long expectedVersion,
        String note
) {}
