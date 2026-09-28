package com.company.bds.engagement.application;

import com.company.bds.engagement.application.port.SavedSearchStorePort;
import com.company.bds.engagement.application.port.SavedSearchStorePort.Match;
import com.company.bds.engagement.application.port.SavedSearchStorePort.SavedSearch;
import com.company.bds.engagement.domain.AlertFrequency;
import com.company.bds.engagement.domain.ListingChange;
import com.company.bds.engagement.domain.VndFormat;
import com.company.bds.notification.NotificationRequest;
import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.notification.domain.NotificationCategory;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.shared.security.ContactInfoGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sends the alerts of saved searches whose digest is due: one in-app notification and at most one e-mail per search and
 * digest (at most {@value #EMAIL_LISTINGS} listings listed, the rest counted). Each search is delivered in its own
 * transaction: the search row is locked with SKIP LOCKED and its pending matches are marked delivered in the same
 * transaction that stores the notification and queues the e-mail, so a digest is sent once even with several
 * instances, and a crash before commit sends nothing (it is retried on the next run). Listings that stopped being
 * public before the digest are left out.
 */
@Service
public class AlertDigestService {
    public static final int EMAIL_LISTINGS = 10;
    public static final Duration DELIVERED_RETENTION = Duration.ofDays(90);
    public static final Duration PENDING_RETENTION = Duration.ofDays(30);

    private final SavedSearchStorePort store;
    private final ListingReadModelPort readModel;
    private final RealtimeNotificationService notifications;
    private final TransactionTemplate transactions;
    private final MeterRegistry meters;
    private final Clock clock;

    public AlertDigestService(SavedSearchStorePort store, ListingReadModelPort readModel,
                              RealtimeNotificationService notifications, PlatformTransactionManager transactionManager,
                              MeterRegistry meters, Clock clock) {
        this.store = store;
        this.readModel = readModel;
        this.notifications = notifications;
        this.transactions = new TransactionTemplate(transactionManager);
        this.meters = meters;
        this.clock = clock;
    }

    /** Delivers every due search (in batches); returns how many digests were sent. */
    public int deliverDue(int maxSearches) {
        int sent = 0;
        int seen = 0;
        while (seen < maxSearches) {
            List<UUID> due = store.dueSearches(clock.instant(), Math.min(100, maxSearches - seen));
            if (due.isEmpty()) break;
            for (UUID id : due) {
                Boolean delivered = transactions.execute(status -> deliver(id));
                if (Boolean.TRUE.equals(delivered)) sent++;
            }
            seen += due.size();
            if (due.size() < 100) break;
        }
        return sent;
    }

    public int purge() {
        Instant now = clock.instant();
        return store.purgeMatches(now.minus(DELIVERED_RETENTION), now.minus(PENDING_RETENTION));
    }

    /** @return true when a digest went out */
    boolean deliver(UUID searchId) {
        Instant now = clock.instant();
        SavedSearch search = store.lockDue(searchId, now).orElse(null);
        if (search == null) return false;
        List<Match> matches = store.takePending(searchId, now);
        store.markDigested(searchId, now, AlertFrequency.valueOf(search.frequency()).nextDigestAfter(now));
        if (matches.isEmpty()) return false;

        // Newest fact per listing; only listings that are public now.
        Map<UUID, Match> latest = new LinkedHashMap<>();
        for (int i = matches.size() - 1; i >= 0; i--) latest.putIfAbsent(matches.get(i).listingId(), matches.get(i));
        Map<UUID, PublicListing> visible = readModel.findByIds(latest.keySet()).stream()
                .collect(Collectors.toMap(PublicListing::listingId, row -> row));
        List<Match> shown = latest.values().stream().filter(m -> visible.containsKey(m.listingId())).toList();
        if (shown.isEmpty()) {
            meters.counter("bds.alerts.digests", "outcome", "nothing_visible").increment();
            return false;
        }
        Map<ListingChange.Kind, Integer> byKind = new EnumMap<>(ListingChange.Kind.class);
        shown.forEach(m -> byKind.merge(m.kind(), 1, Integer::sum));
        String summary = summary(byKind);
        String title = shown.size() + " tin khớp tìm kiếm “" + search.name() + "”";
        String link = "/search?" + query(search.params());
        long lastMatch = matches.stream().mapToLong(Match::id).max().orElse(0);
        String dedupe = "saved-search:" + searchId + ":digest:" + lastMatch;

        notifications.notify(new NotificationRequest(search.userId(), "SAVED_SEARCH_ALERT", title, summary, link, dedupe, false));

        StringBuilder body = new StringBuilder(title).append("\n").append(summary).append("\n\n");
        shown.stream().limit(EMAIL_LISTINGS).forEach(match -> {
            PublicListing row = visible.get(match.listingId());
            body.append("• ").append(label(match.kind())).append(ContactInfoGuard.redact(row.title())).append("\n  ")
                    .append(VndFormat.compact(row.priceVnd(), row.purpose()));
            if (match.kind() == ListingChange.Kind.PRICE_DROP && match.previousPriceVnd() != null) {
                body.append(" (trước: ").append(VndFormat.compact(match.previousPriceVnd(), row.purpose())).append(")");
            }
            if (row.districtName() != null) body.append(" · ").append(row.districtName());
            body.append("\n  ").append(notifications.publicBaseUrl()).append("/listings/").append(row.slug()).append("\n");
        });
        if (shown.size() > EMAIL_LISTINGS) body.append("\n… và ").append(shown.size() - EMAIL_LISTINGS).append(" tin khác.\n");
        body.append("\nXem tất cả kết quả: ").append(notifications.publicBaseUrl()).append(link).append("\n");
        notifications.email(new RealtimeNotificationService.EmailRequest(search.userId(), NotificationCategory.ALERTS, title,
                body.toString(), dedupe, searchId));
        meters.counter("bds.alerts.digests", "outcome", "sent", "frequency", search.frequency()).increment();
        return true;
    }

    private static String summary(Map<ListingChange.Kind, Integer> byKind) {
        List<String> parts = new ArrayList<>();
        if (byKind.containsKey(ListingChange.Kind.NEW)) parts.add(byKind.get(ListingChange.Kind.NEW) + " tin mới");
        if (byKind.containsKey(ListingChange.Kind.PRICE_DROP)) parts.add(byKind.get(ListingChange.Kind.PRICE_DROP) + " tin giảm giá");
        if (byKind.containsKey(ListingChange.Kind.BACK_ON_MARKET)) {
            parts.add(byKind.get(ListingChange.Kind.BACK_ON_MARKET) + " tin hiển thị trở lại");
        }
        return String.join(", ", parts) + ".";
    }

    private static String label(ListingChange.Kind kind) {
        return switch (kind) {
            case NEW -> "[Mới] ";
            case PRICE_DROP -> "[Giảm giá] ";
            case BACK_ON_MARKET -> "[Hiển thị lại] ";
        };
    }

    static String query(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
