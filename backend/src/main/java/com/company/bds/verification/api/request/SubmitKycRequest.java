package com.company.bds.verification.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SubmitKycRequest(
        @NotBlank(message = "Số CCCD không được để trống")
        @Pattern(regexp = "^[0-9]{12}$", message = "Số CCCD phải gồm đúng 12 chữ số")
        String idNumber,

        @NotBlank(message = "Họ tên không được để trống")
        String fullName,

        String dob,
        String address,
        @NotBlank String idCardFrontUrl,
        @NotBlank String idCardBackUrl,
        @NotBlank String selfieUrl
) {}
