package com.company.bds.verification.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record SubmitKycRequest(
        @NotNull(message = "userId không được để trống")
        UUID userId,

        @NotBlank(message = "Số CCCD không được để trống")
        @Pattern(regexp = "^[0-9]{12}$", message = "Số CCCD phải gồm đúng 12 chữ số")
        String idNumber,

        @NotBlank(message = "Họ tên không được để trống")
        String fullName,

        String dob,
        String address,
        String idCardFrontUrl,
        String idCardBackUrl,
        String selfieUrl
) {}
