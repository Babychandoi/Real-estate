package com.company.bds.verification.api.request;

import com.company.bds.verification.domain.model.VerificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitVerificationRequest(
        @NotNull(message = "userId không được để trống")
        UUID userId,

        @NotNull(message = "Loại tài liệu pháp lý không được để trống")
        VerificationType verificationType,

        String certificateNumber,
        String documentUrls,

        @NotBlank(message = "Tên chủ sở hữu trên giấy tờ không được để trống")
        String ownerNameOnDoc
) {}
