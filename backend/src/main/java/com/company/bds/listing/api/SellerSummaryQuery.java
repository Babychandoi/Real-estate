package com.company.bds.listing.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Public seller name and avatar for listing cards, loaded in one query per result page. */
@Component
public class SellerSummaryQuery {
    public record SellerSummary(String displayName, String avatarMediaUrl) {}

    private final JdbcTemplate jdbc;

    public SellerSummaryQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, SellerSummary> byOwnerIds(Collection<UUID> ownerIds) {
        Map<UUID, SellerSummary> sellers = new HashMap<>();
        if (ownerIds.isEmpty()) return sellers;
        Object[] ids = ownerIds.stream().distinct().toArray();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.length, "?"));
        jdbc.query("SELECT id, full_name, avatar_media_url FROM users WHERE id IN (" + placeholders + ")",
                (rs) -> {
                    sellers.put(rs.getObject("id", UUID.class),
                            new SellerSummary(rs.getString("full_name"), rs.getString("avatar_media_url")));
                }, ids);
        return sellers;
    }
}
