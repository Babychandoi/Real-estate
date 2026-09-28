package com.company.bds.media;

import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Public image contract (§10): the original URL plus, once stream S1 generates variants, intrinsic size, a
 * {@code srcset} of resized WebP variants and a placeholder. Images without variants keep {@code srcset: []}.
 */
public record ImageDto(String url, @Nullable Integer width, @Nullable Integer height, List<Source> srcset,
                       @Nullable Placeholder placeholder) {

    public ImageDto {
        Objects.requireNonNull(url, "url");
        srcset = srcset == null ? List.of() : List.copyOf(srcset);
    }

    /** An image known only by its URL (legacy media or no variant pipeline yet). */
    public static ImageDto urlOnly(String url) {
        return new ImageDto(url, null, null, List.of(), null);
    }

    public record Source(String url, int width) {}

    public record Placeholder(@Nullable String dominantColor) {}
}
