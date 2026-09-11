package com.company.bds.lead.api.request;

import jakarta.validation.constraints.NotBlank;

public record ResolveReportRequest(
        @NotBlank(message = "resolutionNote không được để trống")
        String resolutionNote,

        boolean permanentlyLockListing
) {}
