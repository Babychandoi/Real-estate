package com.company.bds.verification.api.request;

import com.company.bds.verification.domain.model.VerificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitVerificationRequest(
        @NotNull(message = "Loại tài liệu pháp lý không được để trống")
        VerificationType verificationType,

        String certificateNumber,
        String documentUrls,

        @NotBlank(message = "Tên chủ sở hữu trên giấy tờ không được để trống")
        String ownerNameOnDoc
) {}
