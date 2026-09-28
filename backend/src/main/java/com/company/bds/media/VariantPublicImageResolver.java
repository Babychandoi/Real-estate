package com.company.bds.media;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Variant-aware {@link PublicImageResolver} (contract §10, S1): one query for the whole list. Processed MinIO images get
 * intrinsic width/height (of the canonical URL), a WebP {@code srcset} ascending by width and a placeholder
 * ({@code dominantColor}, {@code lqip}); legacy/pending/failed MinIO images and external URLs stay URL-only.
 * Replaces {@link UrlOnlyPublicImageResolver} (registered {@code @Fallback}).
 */
@Component
class VariantPublicImageResolver implements PublicImageResolver {
    private final JdbcTemplate jdbc;

    VariantPublicImageResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<ImageDto> resolve(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) return List.of();
        List<String> urls = mediaUrls.stream().filter(url -> url != null && !url.isBlank()).toList();
        Set<String> keys = new LinkedHashSet<>();
        for (String url : urls) {
            String key = MediaKeys.keyOfPublicUrl(url);
            if (key != null && MediaKeys.isOriginal(key)) keys.add(key);
        }
        Map<String, ImageDto> resolved = keys.isEmpty() ? Map.of() : load(keys);
        List<ImageDto> result = new ArrayList<>(urls.size());
        for (String url : urls) {
            String key = MediaKeys.keyOfPublicUrl(url);
            ImageDto dto = key == null ? null : resolved.get(key);
            result.add(dto != null ? dto : ImageDto.urlOnly(url));
        }
        return result;
    }

    private Map<String, ImageDto> load(Set<String> keys) {
        Map<String, Row> rows = new HashMap<>();
        jdbc.query("""
                SELECT m.object_key, m.width, m.height, m.dominant_color, m.lqip, v.variant_key, v.width AS variant_width
                FROM media_objects m
                LEFT JOIN media_variants v ON v.object_key = m.object_key
                WHERE m.object_key = ANY (?) AND m.visibility = 'PUBLIC' AND m.processing_state = 'READY'
                ORDER BY m.object_key, v.width
                """, rs -> {
            Row row = rows.computeIfAbsent(rs.getString("object_key"), key -> {
                try {
                    return new Row((Integer) rs.getObject("width"), (Integer) rs.getObject("height"),
                            rs.getString("dominant_color"), rs.getString("lqip"), new ArrayList<>());
                } catch (java.sql.SQLException ex) {
                    throw new IllegalStateException(ex);
                }
            });
            String variant = rs.getString("variant_key");
            if (variant != null) row.srcset().add(new ImageDto.Source(MediaKeys.publicUrl(variant), rs.getInt("variant_width")));
        }, (Object) keys.toArray(String[]::new));
        Map<String, ImageDto> result = new HashMap<>();
        rows.forEach((key, row) -> result.put(key, new ImageDto(MediaKeys.publicUrl(key), row.width(), row.height(),
                row.srcset(), row.dominantColor() == null && row.lqip() == null ? null
                : new ImageDto.Placeholder(row.dominantColor(), row.lqip()))));
        return result;
    }

    private record Row(Integer width, Integer height, String dominantColor, String lqip, List<ImageDto.Source> srcset) {}
}
