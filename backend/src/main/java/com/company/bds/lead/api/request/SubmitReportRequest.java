package com.company.bds.lead.api.request;

import com.company.bds.lead.domain.model.ReportCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitReportRequest(
        @NotNull(message = "listingId không được để trống")
        UUID listingId,

        @NotNull(message = "category không được để trống")
        ReportCategory category,

        @NotBlank(message = "description không được để trống")
        String description,

        String evidenceUrls,

        String reporterPhone
) {}
