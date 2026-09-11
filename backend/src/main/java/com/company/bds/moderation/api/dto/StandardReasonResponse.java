package com.company.bds.moderation.api.dto;

public record StandardReasonResponse(
        String code,
        String vietnameseLabel,
        String category
) {
}
