package com.company.bds.lead.api.request;

import com.company.bds.lead.domain.model.LeadStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateLeadStatusRequest(
        @NotNull(message = "Trạng thái mới không được để trống")
        LeadStatus status
) {}
