package com.company.bds.lead.api.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * DTO yêu cầu gửi thông tin tư vấn BĐS của khách hàng.
 * Áp dụng Bean Validation với key thông báo trong i18n/validation*.properties.
 */
public record CreateLeadRequest(
        @NotNull(message = "{validation.listing.purpose.required}")
        UUID listingId,

        @NotBlank(message = "{validation.lead.fullName.required}")
        String fullName,

        @NotBlank(message = "{validation.lead.phone.required}")
        @Pattern(regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$", message = "{validation.lead.phone.invalid}")
        String phone,

        String note,

        @AssertTrue(message = "{validation.lead.consent.required}")
        boolean consentPolicy
) {}
