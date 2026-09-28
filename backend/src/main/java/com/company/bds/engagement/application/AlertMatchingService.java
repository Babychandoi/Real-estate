package com.company.bds.engagement.application;

import com.company.bds.engagement.application.port.SavedListingStorePort;
import com.company.bds.engagement.application.port.SavedSearchStorePort;
import com.company.bds.engagement.application.port.SavedSearchStorePort.Candidate;
import com.company.bds.engagement.domain.ListingChange;
import com.company.bds.engagement.domain.ListingChange.Fact;
import com.company.bds.engagement.domain.VndFormat;
import com.company.bds.notification.NotificationRequest;
import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.domain.InvalidFilterException;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilter;
import com.company.bds.shared.security.ContactInfoGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a change of a listing's public state into alerts (queue {@code engage-listing-change}, fed by the V070 trigger on
 * {@code listing_public_read}). The listing's committed public row is compared with the last state seen (locked, so two
 * workers never classify the same change twice); the fact is then matched against the saved searches (coarse SQL filter,
 * then {@link SearchFilter#matches} — the predicate the search API itself re-checks with) and recorded once per
 * (search, listing, kind, fact). Price drops and returns are also sent to the users who saved the listing.
 */
@Service
public class AlertMatchingService {
    static final int CANDIDATE_BATCH = 500;
    static final int SAVER_BATCH = 500;

    private final SavedSearchStorePort searches;
    private final SavedListingStorePort saved;
    private final ListingReadModelPort readModel;
    private final RealtimeNotificationService notifications;
    private final MeterRegistry meters;
    private final Clock clock;

    public AlertMatchingService(SavedSearchStorePort searches, SavedListingStorePort saved, ListingReadModelPort readModel,
                                RealtimeNotificationService notifications, MeterRegistry meters, Clock clock) {
        this.searches = searches;
        this.saved = saved;
        this.readModel = readModel;
        this.notifications = notifications;
        this.meters = meters;
        this.clock = clock;
    }

    /** @return the classified fact, if the change was alert-worthy */
    @Transactional
    public Optional<Fact> process(UUID listingId) {
        if (!searches.listingExists(listingId)) return Optional.empty();
        Optional<ListingChange.Seen> seen = searches.lockState(listingId);
        List<PublicListing> rows = readModel.findByIds(List.of(listingId));
        Instant now = clock.instant();
        if (rows.isEmpty()) {
            if (seen.isPresent() && seen.get().visible()) {
                searches.saveState(listingId, false, null, null, now, now);
            }
            return Optional.empty();
        }
        PublicListing row = rows.get(0);
        Optional<Fact> fact = ListingChange.classify(seen.orElse(null),
                new ListingChange.Current(row.purpose(), row.priceVnd(), row.publishedAt()), now);
        searches.saveState(listingId, true, row.purpose(), row.priceVnd(), null, now);
        fact.ifPresent(f -> {
            meters.counter("bds.alerts.facts", "kind", f.kind().name()).increment();
            int matched = matchSearches(row, f, now);
            meters.counter("bds.alerts.matches", "kind", f.kind().name()).increment(matched);
            if (f.kind() != ListingChange.Kind.NEW) notifySavers(row, f);
        });
        return fact;
    }

    private int matchSearches(PublicListing row, Fact fact, Instant now) {
        int matched = 0;
        UUID after = null;
        while (true) {
            List<Candidate> batch = searches.candidates(fact.kind(), row.purpose(), row.propertyType(),
                    row.districtCode() == null ? "" : row.districtCode(), row.priceVnd(), row.ownerId(), now, after,
                    CANDIDATE_BATCH);
            for (Candidate candidate : batch) {
                SearchFilter filter;
                try {
                    filter = SavedSearchService.filterOf(candidate.params());
                } catch (InvalidFilterException ex) {
                    // Stored before a schema change made it invalid: never matches, the user can re-save it.
                    continue;
                }
                if (filter.matches(row, now) && searches.insertMatch(candidate.id(), row.listingId(), fact.kind(),
                        fact.factKey(), row.priceVnd(), fact.previousPriceVnd(), now)) {
                    matched++;
                }
            }
            if (batch.size() < CANDIDATE_BATCH) return matched;
            after = batch.get(batch.size() - 1).id();
        }
    }

    private void notifySavers(PublicListing row, Fact fact) {
        String title = ContactInfoGuard.redact(row.title());
        String type;
        String heading;
        String message;
        if (fact.kind() == ListingChange.Kind.PRICE_DROP) {
            type = "SAVED_LISTING_PRICE_DROP";
            heading = "Tin đã lưu vừa giảm giá";
            message = "“" + title + "”: " + VndFormat.compact(fact.previousPriceVnd(), row.purpose()) + " → "
                    + VndFormat.compact(row.priceVnd(), row.purpose()) + ".";
        } else {
            type = "SAVED_LISTING_BACK_ON_MARKET";
            heading = "Tin đã lưu hiển thị trở lại";
            message = "“" + title + "” đang hiển thị lại với giá " + VndFormat.compact(row.priceVnd(), row.purpose()) + ".";
        }
        String link = "/listings/" + row.slug();
        UUID after = null;
        while (true) {
            List<UUID> users = saved.savers(row.listingId(), after, SAVER_BATCH);
            for (UUID userId : users) {
                if (userId.equals(row.ownerId())) continue;
                notifications.notify(new NotificationRequest(userId, type, heading, message, link,
                        "saved-listing:" + row.listingId() + ":" + fact.factKey(), true));
            }
            if (users.size() < SAVER_BATCH) return;
            after = users.get(users.size() - 1);
        }
    }
}
