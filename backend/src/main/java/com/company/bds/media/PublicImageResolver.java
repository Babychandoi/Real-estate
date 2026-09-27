package com.company.bds.media;

import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Turns stored media URLs into {@link ImageDto}s for public responses (contract §10). Implementations resolve a whole list
 * with at most one query (never one per image), keep the input order and skip null/blank URLs. The default bean is
 * {@link UrlOnlyPublicImageResolver}; stream S1 provides the variant-aware resolver, which replaces it automatically.
 */
public interface PublicImageResolver {

    List<ImageDto> resolve(List<String> mediaUrls);

    default Optional<ImageDto> resolveOne(@Nullable String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) return Optional.empty();
        return resolve(List.of(mediaUrl)).stream().findFirst();
    }
}
