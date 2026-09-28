package com.company.bds.media;

import org.springframework.lang.Nullable;

import java.util.regex.Pattern;

/** Object keys and URLs of MinIO media: {@code <uuid>.<ext>} originals and {@code <uuid>__w<width>.webp} variants. */
public final class MediaKeys {
    public static final String PUBLIC_PREFIX = "/api/v1/public/media/";
    public static final String SIGNED_PREFIX = "/api/v1/media/signed/";
    /** Path-variable regex for controllers: an original or a variant key. */
    public static final String ANY_KEY_REGEX = "[0-9a-fA-F-]{36}(?:__w[0-9]{2,4})?\\.(?:jpg|png|webp|avif)";
    private static final Pattern ORIGINAL = Pattern.compile("[0-9a-fA-F-]{36}\\.(?:jpg|png|webp|avif)");
    private static final Pattern VARIANT = Pattern.compile("([0-9a-fA-F-]{36})__w([0-9]{2,4})\\.webp");

    private MediaKeys() {}

    public static boolean isOriginal(String key) { return key != null && ORIGINAL.matcher(key).matches(); }

    public static boolean isVariant(String key) { return key != null && VARIANT.matcher(key).matches(); }

    public static boolean isValid(String key) { return isOriginal(key) || isVariant(key); }

    /** {@code <uuid>.jpg} → {@code <uuid>__w640.webp}. */
    public static String variantKey(String originalKey, int width) {
        int dot = originalKey.lastIndexOf('.');
        return originalKey.substring(0, dot) + "__w" + width + ".webp";
    }

    /** Object key of a public media URL (original or variant), or {@code null} for any other URL. */
    @Nullable
    public static String keyOfPublicUrl(@Nullable String url) {
        if (url == null || !url.startsWith(PUBLIC_PREFIX)) return null;
        String key = url.substring(PUBLIC_PREFIX.length());
        return isValid(key) ? key : null;
    }

    public static String publicUrl(String key) { return PUBLIC_PREFIX + key; }
}
