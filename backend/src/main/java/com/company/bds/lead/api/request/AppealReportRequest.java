package com.company.bds.lead.api.request;

import jakarta.validation.constraints.NotBlank;

public record AppealReportRequest(
        @NotBlank(message = "newEvidence không được để trống")
        String newEvidence
) {}
