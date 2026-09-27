package com.company.bds.media;

import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolver used until stream S1 ships variants: no query, the URL as is, {@code srcset: []}, width/height/placeholder null.
 * {@link Fallback}: any other {@link PublicImageResolver} bean takes precedence without touching this class.
 */
@Component
@Fallback
class UrlOnlyPublicImageResolver implements PublicImageResolver {

    @Override
    public List<ImageDto> resolve(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) return List.of();
        return mediaUrls.stream().filter(url -> url != null && !url.isBlank()).map(ImageDto::urlOnly).toList();
    }
}
