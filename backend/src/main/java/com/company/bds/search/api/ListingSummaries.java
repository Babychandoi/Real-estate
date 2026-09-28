package com.company.bds.search.api;

import com.company.bds.media.ImageDto;
import com.company.bds.search.api.response.ListingV2Responses.ListingSummaryV2;
import com.company.bds.search.application.ListingReadService;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.domain.PublicListing;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public card summaries ({@code ListingSummaryV2}) for a set of listing ids, for other modules' APIs (saved listings,
 * shortlists, alerts). Only publicly visible listings (read model + seller ACTIVE) are returned: two statements at most
 * (rows + image resolver), whatever the number of ids.
 */
@Component
public class ListingSummaries {
    private final ListingReadModelPort readModel;
    private final ListingReadService reads;
    private final Clock clock;

    public ListingSummaries(ListingReadModelPort readModel, ListingReadService reads, Clock clock) {
        this.readModel = readModel;
        this.reads = reads;
        this.clock = clock;
    }

    /** Summaries keyed by listing id, in the order of {@code ids}; ids that are not public are absent. */
    public Map<UUID, ListingSummaryV2> byIds(Collection<UUID> ids) {
        Map<UUID, ListingSummaryV2> out = new LinkedHashMap<>();
        if (ids.isEmpty()) return out;
        List<PublicListing> rows = readModel.findByIds(ids);
        Map<String, ImageDto> thumbnails = reads.thumbnails(rows);
        Instant now = clock.instant();
        Map<UUID, ListingSummaryV2> found = new LinkedHashMap<>();
        for (PublicListing row : rows) {
            found.put(row.listingId(), ListingV2Mapper.summary(row,
                    row.thumbnailUrl() == null ? null : thumbnails.get(row.thumbnailUrl()), now));
        }
        for (UUID id : ids) {
            ListingSummaryV2 summary = found.get(id);
            if (summary != null) out.put(id, summary);
        }
        return out;
    }
}
