package com.company.bds.listing.domain.model;

import java.util.UUID;

/**
 * Value Object biểu diễn hình ảnh đính kèm của một revision tin đăng.
 */
public record ListingMedia(
        UUID id,
        String mediaUrl,
        boolean isPrimary,
        int sortOrder
) {}
