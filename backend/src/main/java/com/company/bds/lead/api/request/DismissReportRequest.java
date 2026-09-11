package com.company.bds.lead.api.request;

import jakarta.validation.constraints.NotBlank;

public record DismissReportRequest(
        @NotBlank(message = "dismissNote không được để trống")
        String dismissNote,

        boolean resumeListing
) {}
