package com.company.bds.listing.application.service;

import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.exception.ListingDomainException;
import com.company.bds.listing.domain.model.FreshnessPolicy;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.jobs.JobQueue;
import com.company.bds.shared.mail.MailMessage;
import com.company.bds.shared.mail.MailOutbox;
import com.company.bds.shared.security.PiiProtectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Freshness of published listings (P-14): owner confirmation, expiry, reminders, renewal and the "đã bán" check.
 * Every state change goes through an UPDATE of {@code listings}, so S2's read-model triggers see it.
 */
@Service
public class ListingFreshnessService {
    public static final String REMINDER_QUEUE = "listing-expiry-reminder";
    public static final String SOLD_CHECK_QUEUE = "listing-sold-check";
    private static final Logger log = LoggerFactory.getLogger(ListingFreshnessService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final int SWEEP_BATCH = 500;

    private final ListingPersistencePort listings;
    private final JdbcTemplate jdbc;
    private final JobQueue jobs;
    private final MailOutbox mail;
    private final RealtimeNotificationService notifications;
    private final Clock clock;
    private final PiiProtectionService pii;

    public ListingFreshnessService(ListingPersistencePort listings, JdbcTemplate jdbc, JobQueue jobs, MailOutbox mail,
                                   RealtimeNotificationService notifications, Clock clock, PiiProtectionService pii) {
        this.listings = listings;
        this.jdbc = jdbc;
        this.jobs = jobs;
        this.mail = mail;
        this.notifications = notifications;
        this.clock = clock;
        this.pii = pii;
    }

    @Transactional
    public Listing confirmAvailability(UUID listingId, UUID ownerId) {
        Listing listing = owned(listingId, ownerId);
        listing.confirmAvailability(clock.instant());
        return listings.save(listing);
    }

    @Transactional
    public Listing renew(UUID listingId, UUID ownerId) {
        Listing listing = owned(listingId, ownerId);
        listing.renew(clock.instant());
        return listings.save(listing);
    }

    /**
     * Called when a public FAKE_SOLD report is filed: asks the owner to confirm within 48 hours, otherwise the listing is
     * paused by {@link #pauseUnansweredSoldChecks}. A second report while a check is open changes nothing.
     */
    @Transactional
    public void requestSoldConfirmation(UUID listingId) { requestSoldConfirmation(listingId, null); }

    /** {@code reporterPhone} is the current report's reporter (it may not be flushed yet when this runs). */
    @Transactional
    public void requestSoldConfirmation(UUID listingId, String reporterPhone) {
        Listing listing = listings.findById(listingId).orElse(null);
        if (listing == null) return;
        Instant now = clock.instant();
        Instant cleared = listing.getSoldCheckClearedAt();
        if (cleared != null && cleared.isAfter(now.minus(FreshnessPolicy.SOLD_CHECK_COOLDOWN))) {
            // The owner answered a check recently: a new one needs reports from at least two distinct reporters.
            // Phones are stored as randomized ciphertext, so distinctness is decided on the decrypted, normalized values.
            Set<String> reporters = new HashSet<>();
            jdbc.queryForList("""
                    SELECT reporter_phone FROM listing_reports
                    WHERE listing_id = ? AND category = 'FAKE_SOLD' AND created_at > ? AND reporter_phone IS NOT NULL
                    """, String.class, listingId, Timestamp.from(cleared)).forEach(p -> reporters.add(phoneKey(p)));
            if (reporterPhone != null) reporters.add(phoneKey(reporterPhone));
            if (reporters.size() < 2) return;
        }
        if (!listing.requestSoldCheck(now)) return;
        Listing saved = listings.save(listing);
        Instant due = saved.getSoldCheckDueAt();
        String title = "Xác nhận tin còn hiệu lực";
        String message = "Có người báo bất động sản trong tin \"" + titleOf(saved) + "\" đã bán hoặc không còn. Vui lòng xác nhận "
                + "còn hàng hoặc ẩn tin trước " + DATE.format(due) + " (48 giờ); nếu không, tin sẽ tạm ẩn.";
        notifications.notify(saved.getOwnerId(), "LISTING_SOLD_CHECK", title, message);
        email(saved.getOwnerId(), title, message + "\n\nVào mục \"Tin đăng của tôi\" trên Nhà Đất Chuẩn để xác nhận.",
                "LISTING_SOLD_CHECK", "sold-check:" + saved.getId() + ":" + due.getEpochSecond());
        jobs.enqueue(SOLD_CHECK_QUEUE, saved.getId() + ":" + due.getEpochSecond(),
                Map.of("listingId", saved.getId().toString(), "dueAt", due.toString()), due);
    }

    private String phoneKey(String stored) {
        String plain = stored.startsWith("v1:") ? pii.reveal(stored) : stored;
        return plain.replaceAll("\\s+", "");
    }

    /** Pauses ACTIVE listings whose sold check passed its deadline unanswered. Returns the paused ids. */
    @Transactional
    public List<UUID> pauseUnansweredSoldChecks(Instant now) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                UPDATE listings SET status = 'PAUSED', sold_check_due_at = NULL, version = version + 1, updated_at = ?
                WHERE id IN (SELECT id FROM listings WHERE status = 'ACTIVE' AND sold_check_due_at <= ?
                             ORDER BY sold_check_due_at LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING id, owner_id
                """, Timestamp.from(now), Timestamp.from(now), SWEEP_BATCH);
        for (Map<String, Object> row : rows) {
            notifications.notify((UUID) row.get("owner_id"), "LISTING_AUTO_PAUSED", "Tin đã tạm ẩn",
                    "Tin chưa được xác nhận còn hàng trong 48 giờ sau khi có báo cáo đã bán, nên đã tạm ẩn. "
                            + "Bạn có thể hiện lại tin nếu bất động sản vẫn còn.");
        }
        return rows.stream().map(row -> (UUID) row.get("id")).toList();
    }

    /** ACTIVE → EXPIRED for listings past {@code expires_at}. Returns the expired ids. */
    @Transactional
    public List<UUID> expireDue(Instant now) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                UPDATE listings SET status = 'EXPIRED', version = version + 1, updated_at = ?
                WHERE id IN (SELECT id FROM listings WHERE status = 'ACTIVE' AND expires_at <= ?
                             ORDER BY expires_at LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING id, owner_id
                """, Timestamp.from(now), Timestamp.from(now), SWEEP_BATCH);
        for (Map<String, Object> row : rows) {
            notifications.notify((UUID) row.get("owner_id"), "LISTING_EXPIRED", "Tin đã hết hạn hiển thị",
                    "Tin đã quá 45 ngày kể từ lần xác nhận còn hàng gần nhất. Gia hạn trong 30 ngày để hiển thị lại "
                            + "mà không cần duyệt lại (nếu nội dung không đổi).");
        }
        if (!rows.isEmpty()) log.info("listings_expired count={}", rows.size());
        return rows.stream().map(row -> (UUID) row.get("id")).toList();
    }

    /**
     * Queues the 7-day and 2-day reminders of every ACTIVE listing that entered a reminder window. The job key is
     * {@code listing:cycle:kind} (cycle = expires_at), enqueued once; the handler re-checks the state before sending.
     */
    @Transactional
    public int scheduleReminders(Instant now) {
        int queued = 0;
        Timestamp lastExpires = Timestamp.from(now);
        UUID lastId = new UUID(0, 0);
        while (true) {
            // Keyset progress over (expires_at, id): every listing in the window is visited once per scan.
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT id, expires_at FROM listings
                    WHERE status = 'ACTIVE' AND expires_at <= ? AND (expires_at, id) > (?, ?)
                    ORDER BY expires_at, id LIMIT ?
                    """, Timestamp.from(now.plus(FreshnessPolicy.FIRST_REMINDER)), lastExpires, lastId, reminderBatch);
            if (rows.isEmpty()) break;
            queued += queueBatch(rows, now);
            Map<String, Object> last = rows.get(rows.size() - 1);
            lastExpires = (Timestamp) last.get("expires_at");
            lastId = (UUID) last.get("id");
            if (rows.size() < reminderBatch) break;
        }
        return queued;
    }

    private int reminderBatch = 500;

    /** Batch size of the reminder scan (tests use a small one). */
    public void setReminderBatch(int size) { this.reminderBatch = size; }

    private int queueBatch(List<Map<String, Object>> rows, Instant now) {
        int queued = 0;
        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            Instant expires = ((Timestamp) row.get("expires_at")).toInstant();
            queued += queueReminder(id, expires, "D7", expires.minus(FreshnessPolicy.FIRST_REMINDER), now);
            if (!expires.minus(FreshnessPolicy.SECOND_REMINDER).isAfter(now.plus(FreshnessPolicy.FIRST_REMINDER))) {
                queued += queueReminder(id, expires, "D2", expires.minus(FreshnessPolicy.SECOND_REMINDER), now);
            }
        }
        return queued;
    }

    private int queueReminder(UUID id, Instant cycle, String kind, Instant runAt, Instant now) {
        // Skip the 7-day reminder when the listing is already inside the 2-day window (one reminder is enough).
        if (kind.equals("D7") && !cycle.minus(FreshnessPolicy.SECOND_REMINDER).isAfter(now)) return 0;
        Optional<UUID> job = jobs.enqueueOnce(REMINDER_QUEUE, id + ":" + cycle.getEpochSecond() + ":" + kind,
                Map.of("listingId", id.toString(), "cycle", cycle.toString(), "kind", kind),
                // Already due: let the database stamp run_at (the worker claims with run_at <= now() on the DB clock).
                runAt.isAfter(now) ? runAt : null);
        return job.isPresent() ? 1 : 0;
    }

    /** Sends one reminder if the listing is still ACTIVE in the same cycle and it was not sent before. */
    @Transactional
    public boolean sendReminder(UUID listingId, Instant cycle, String kind) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT owner_id, status, expires_at FROM listings WHERE id = ?", listingId);
        if (rows.isEmpty()) return false;
        Map<String, Object> row = rows.get(0);
        Timestamp expires = (Timestamp) row.get("expires_at");
        if (!"ACTIVE".equals(row.get("status")) || expires == null || !expires.toInstant().equals(cycle)) return false;
        int inserted = jdbc.update("""
                INSERT INTO listing_expiry_reminders (listing_id, cycle_expires_at, kind) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, listingId, Timestamp.from(cycle), kind);
        if (inserted == 0) return false;
        UUID ownerId = (UUID) row.get("owner_id");
        String title = kind.equals("D7") ? "Tin sắp hết hạn sau 7 ngày" : "Tin sắp hết hạn sau 2 ngày";
        String message = "Tin \"" + titleOf(listingId) + "\" hết hạn hiển thị ngày " + DATE.format(cycle)
                + ". Bấm \"Xác nhận còn hàng\" để tiếp tục hiển thị thêm 45 ngày.";
        notifications.notify(ownerId, "LISTING_EXPIRY_REMINDER", title, message);
        email(ownerId, title, message + "\n\nVào mục \"Tin đăng của tôi\" trên Nhà Đất Chuẩn để xác nhận.",
                "LISTING_EXPIRY_REMINDER", "expiry:" + listingId + ":" + cycle.getEpochSecond() + ":" + kind);
        return true;
    }

    private void email(UUID userId, String subject, String body, String category, String dedupeKey) {
        List<String> emails = jdbc.queryForList("SELECT email FROM users WHERE id = ? AND email IS NOT NULL", String.class, userId);
        if (emails.isEmpty()) return;
        mail.tryEnqueue(MailMessage.text(emails.get(0), subject, body, category, dedupeKey));
    }

    private Listing owned(UUID listingId, UUID ownerId) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new ListingDomainException("LISTING_NOT_FOUND", "Không tìm thấy tin đăng."));
        if (!listing.getOwnerId().equals(ownerId)) {
            throw new ListingDomainException("LISTING_NOT_FOUND", "Không tìm thấy tin đăng.");
        }
        return listing;
    }

    private static String titleOf(Listing listing) {
        return listing.getPublicRevision().or(listing::getLatestRevision).map(r -> r.getTitle()).orElse("");
    }

    private String titleOf(UUID listingId) {
        List<String> titles = jdbc.queryForList("""
                SELECT r.title FROM listings l JOIN listing_revisions r ON r.id = COALESCE(l.public_revision_id,
                    (SELECT id FROM listing_revisions x WHERE x.listing_id = l.id ORDER BY x.revision_number DESC LIMIT 1))
                WHERE l.id = ?
                """, String.class, listingId);
        return titles.isEmpty() ? "" : titles.get(0);
    }
}
