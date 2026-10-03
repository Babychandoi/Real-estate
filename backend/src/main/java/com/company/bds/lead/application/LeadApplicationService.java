package com.company.bds.lead.application;

import com.company.bds.analytics.application.AnalyticsRecorder;
import com.company.bds.iam.application.AuthService;
import com.company.bds.lead.domain.model.Lead;
import com.company.bds.lead.domain.model.LeadRequestType;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.lead.domain.port.LeadPersistencePort;
import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.jobs.JobQueue;
import com.company.bds.shared.outbox.OutboxEventWriter;
import com.company.bds.shared.security.PiiProtectionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Lead submission (F17) and the legacy owner/staff read paths.
 *
 * <p>Submission policy (documented in {@code streams/s3b-leads.md}):</p>
 * <ul>
 *   <li><b>Pause vs lead (F17.3):</b> the listing row is locked {@code FOR SHARE} and must be ACTIVE with a public
 *   revision and an unelapsed deadline inside the lock. A pause/hide committed first refuses a new lead; a pause arriving while the lead is being
 *   inserted waits for it, and the lead stays valid.</li>
 *   <li><b>Idempotency (F17.2):</b> the key is bound to scope {@code lead:<actor>} (actor + route) and the SHA-256 of the
 *   canonical payload, kept {@link #IDEMPOTENCY_TTL}. Concurrent duplicates wait on the key row and replay the winner's
 *   lead; another payload with the same key is refused; another actor never sees the replay.</li>
 *   <li><b>Quota (F17.1):</b> {@link #QUOTA_PER_DAY} leads per phone and per requester account in a rolling 24 h window,
 *   counted and inserted under transaction-scoped advisory locks on both keys (fixed order), so parallel requests cannot
 *   exceed it. Replays never count.</li>
 * </ul>
 */
@Service
@Transactional
public class LeadApplicationService {
    public static final int QUOTA_PER_DAY = 10;
    public static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String KEY_PATTERN = "[A-Za-z0-9._:-]{8,128}";

    /** Result of a submission; {@code replayed} when an earlier request with the same key produced the lead. */
    public record SubmitResult(Lead lead, boolean replayed) {}

    private final LeadPersistencePort leadPersistencePort;
    private final ListingPersistencePort listingPersistencePort;
    private final PiiProtectionService piiProtection;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<OutboxEventWriter> outbox;
    private final AnalyticsRecorder analytics;
    private final LeadAccessService access;
    private final ObjectProvider<RealtimeNotificationService> notifications;
    private final Clock clock;
    private final JobQueue jobs;

    public LeadApplicationService(
            LeadPersistencePort leadPersistencePort,
            ListingPersistencePort listingPersistencePort,
            PiiProtectionService piiProtection,
            JdbcTemplate jdbc,
            ObjectProvider<OutboxEventWriter> outbox,
            AnalyticsRecorder analytics,
            LeadAccessService access,
            ObjectProvider<RealtimeNotificationService> notifications,
            Clock clock,
            JobQueue jobs) {
        this.leadPersistencePort = leadPersistencePort;
        this.listingPersistencePort = listingPersistencePort;
        this.piiProtection = piiProtection;
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.analytics = analytics;
        this.access = access;
        this.notifications = notifications;
        this.clock = clock;
        this.jobs = jobs;
    }

    /** Backward-compatible entry point (returns only the lead). */
    public Lead submitLead(UUID requesterId, UUID listingId, String fullName, String rawPhone, LeadRequestType requestType,
                           String note, boolean consentPolicy, String idempotencyKey) {
        return submit(requesterId, listingId, fullName, rawPhone, requestType, note, consentPolicy, idempotencyKey).lead();
    }

    public SubmitResult submit(UUID requesterId, UUID listingId, String fullName, String rawPhone, LeadRequestType requestType,
                               String note, boolean consentPolicy, @Nullable String idempotencyKey) {
        Objects.requireNonNull(requesterId, "requesterId");
        LeadRequestType type = requestType != null ? requestType : LeadRequestType.CONSULTATION;
        String name = fullName.trim();
        String phone = rawPhone.replaceAll("\\s+", "");
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 1000) {
            throw ApiException.badRequest("NOTE_TOO_LONG", "Lời nhắn tối đa 1000 ký tự.");
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && !key.matches(KEY_PATTERN)) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key không hợp lệ.");
        }
        // 1. Hold owner and listing rows through validation/insert. A suspension committed first rejects a new lead;
        // one arriving after these locks waits. Status changes enqueue indexing but never lock listings.
        List<Map<String, Object>> listingRows = jdbc.queryForList("""
                SELECT l.owner_id, l.status, l.public_revision_id, l.expires_at, u.status AS owner_status
                FROM users u JOIN listings l ON l.owner_id = u.id WHERE l.id = ? FOR SHARE OF u, l
                """, listingId);
        if (listingRows.isEmpty()) throw ApiException.notFound("LISTING_NOT_FOUND", "Tin đăng không tồn tại.");
        Map<String, Object> listing = listingRows.get(0);
        UUID ownerId = (UUID) listing.get("owner_id");
        Instant now = clock.instant();

        // 2. Idempotency bound to the actor and route; concurrent duplicates block on the key row until the winner ends.
        UUID leadId = UUID.randomUUID();
        if (key != null) {
            String scope = "lead:" + requesterId;
            String requestHash = AuthService.sha256(String.join("|", listingId.toString(), name, phone, type.name(),
                    Objects.toString(cleanNote, ""), String.valueOf(consentPolicy)));
            int claimed = jdbc.update("""
                    INSERT INTO api_idempotency_keys(scope, idempotency_key, request_hash, resource_id, created_at, expires_at)
                    VALUES (?,?,?,?,?,?)
                    ON CONFLICT (scope, idempotency_key) DO UPDATE
                        SET request_hash = EXCLUDED.request_hash, resource_id = EXCLUDED.resource_id,
                            created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at
                        WHERE COALESCE(api_idempotency_keys.expires_at, api_idempotency_keys.created_at + interval '24 hours') <= ?
                    """, scope, key, requestHash, leadId, Timestamp.from(now), Timestamp.from(now.plus(IDEMPOTENCY_TTL)),
                    Timestamp.from(now));
            if (claimed == 0) {
                Map<String, Object> existing = jdbc.queryForMap(
                        "SELECT request_hash, resource_id FROM api_idempotency_keys WHERE scope = ? AND idempotency_key = ?",
                        scope, key);
                if (!requestHash.equals(existing.get("request_hash"))) {
                    throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key đã được dùng cho yêu cầu khác.");
                }
                Lead previous = leadPersistencePort.findById((UUID) existing.get("resource_id"))
                        .orElseThrow(() -> ApiException.conflict("IDEMPOTENCY_IN_PROGRESS", "Yêu cầu đang được xử lý; vui lòng thử lại."));
                // Keep legacy hashes valid across deployment, but never let a delimiter collision replay another
                // payload. The committed lead retains every canonical field, including the protected phone index.
                if (!requesterId.equals(previous.getRequesterId()) || !listingId.equals(previous.getListingId())
                        || !name.equals(previous.getFullName())
                        || !piiProtection.blindIndex(phone).equals(previous.getPhoneLookupHash())
                        || type != previous.getRequestType() || !Objects.equals(cleanNote, previous.getNote())
                        || consentPolicy != previous.isConsentPolicy()) {
                    throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key đã được dùng cho yêu cầu khác.");
                }
                return new SubmitResult(previous, true);
            }
        }

        // Only new submissions need current eligibility; an exact actor-scoped replay returns its committed lead.
        if (!acceptsLeads(listing, clock.instant())) {
            throw ApiException.conflict("LISTING_NOT_ACCEPTING_LEADS", "Tin đăng không còn nhận yêu cầu liên hệ.");
        }
        if (ownerId.equals(requesterId)) {
            throw ApiException.conflict("SELF_LEAD", "Bạn không thể gửi yêu cầu liên hệ cho tin của chính mình.");
        }
        requireVerifiedKyc(requesterId, "KYC_REQUIRED", "Bạn cần hoàn tất eKYC trước khi gửi yêu cầu liên hệ.");
        requireVerifiedKyc(ownerId, "OWNER_KYC_REQUIRED",
                "Người đăng chưa hoàn tất eKYC nên tin này tạm thời chưa nhận yêu cầu liên hệ.");

        // 3. Atomic quota: advisory locks on both keys (sorted, so two transactions never wait on each other in a cycle).
        String lookupHash = piiProtection.blindIndex(phone);
        List<String> lockKeys = new ArrayList<>(List.of("lead-quota:phone:" + lookupHash, "lead-quota:user:" + requesterId));
        Collections.sort(lockKeys);
        for (String lockKey : lockKeys) {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", lockKey);
        }
        Timestamp since = Timestamp.from(now.minus(Duration.ofHours(24)));
        Long phoneCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM leads WHERE phone_lookup_hash = ? AND created_at > ?", Long.class, lookupHash, since);
        Long requesterCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM leads WHERE requester_id = ? AND created_at > ?", Long.class, requesterId, since);
        if ((phoneCount != null && phoneCount >= QUOTA_PER_DAY) || (requesterCount != null && requesterCount >= QUOTA_PER_DAY)) {
            throw ApiException.tooManyRequests("LEAD_QUOTA_EXCEEDED",
                    "Bạn đã gửi quá " + QUOTA_PER_DAY + " yêu cầu liên hệ trong 24 giờ. Vui lòng thử lại sau.");
        }

        // 4. Insert the lead, its first history entry, the analytics fact and the owner notification in one transaction.
        PiiProtectionService.ProtectedValue protectedPhone = piiProtection.protect(phone);
        Lead lead = new Lead(leadId, listingId, requesterId, name, protectedPhone.encrypted(), protectedPhone.blindIndex(),
                type, cleanNote, consentPolicy, LeadStatus.NEW, now);
        // Plain JDBC (not the JPA adapter): the history row below references the lead, so it must exist right now.
        jdbc.update("""
                INSERT INTO leads(id, listing_id, requester_id, full_name, phone_encrypted, phone_lookup_hash, request_type,
                                  note, consent_policy, status, created_at, updated_at, version)
                VALUES (?,?,?,?,?,?,?,?,?,'NEW',?,?,0)
                """, lead.getId(), listingId, requesterId, name, lead.getPhoneEncrypted(), lead.getPhoneLookupHash(),
                type.name(), cleanNote, consentPolicy, Timestamp.from(now), Timestamp.from(now));
        Lead saved = lead;
        access.recordEvent(saved.getId(), "CREATED", requesterId, "REQUESTER", null, "NEW", null,
                Map.of("requestType", type.name()), false, now);
        analytics.recordServer("lead_submitted", 1, saved.getId().toString(), requesterId, listingId,
                Map.of("leadId", saved.getId().toString(), "requestType", saved.getRequestType().name()));
        OutboxEventWriter writer = outbox.getIfAvailable();
        if (writer != null) {
            writer.append("LEAD", saved.getId(), "LEAD_CREATED", Map.of(
                    "leadId", saved.getId(), "listingId", saved.getListingId(), "createdAt", saved.getCreatedAt()));
        }
        LeadSlaReminderHandler.schedule(jdbc, jobs, saved.getId(), ownerId, now);
        RealtimeNotificationService notifier = notifications.getIfAvailable();
        if (notifier != null) {
            notifier.notify(ownerId, "LEAD_RECEIVED", "Có yêu cầu liên hệ mới",
                    (type == LeadRequestType.VIEWING ? "Khách muốn hẹn xem" : "Khách cần tư vấn")
                            + " một tin đăng của bạn. Mở Hộp thư khách quan tâm để phản hồi.");
        }
        return new SubmitResult(saved, false);
    }

    private static boolean acceptsLeads(Map<String, Object> listing, Instant now) {
        Timestamp expiry = (Timestamp) listing.get("expires_at");
        return "ACTIVE".equals(listing.get("owner_status")) && "ACTIVE".equals(listing.get("status"))
                && listing.get("public_revision_id") != null
                && (expiry == null || expiry.toInstant().isAfter(now));
    }

    /** What the contact form needs to know before the visitor types anything (DS-11, F17.4). */
    @Transactional(readOnly = true)
    public Map<String, Object> eligibility(UUID requesterId, UUID listingId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT l.owner_id, l.status, l.public_revision_id, l.expires_at, u.status AS owner_status
                FROM users u JOIN listings l ON l.owner_id = u.id WHERE l.id = ?
                """, listingId);
        if (rows.isEmpty()) throw ApiException.notFound("LISTING_NOT_FOUND", "Tin đăng không tồn tại.");
        UUID ownerId = (UUID) rows.get(0).get("owner_id");
        boolean accepting = acceptsLeads(rows.get(0), clock.instant());
        List<Map<String, Object>> open = jdbc.queryForList("""
                SELECT id, status, created_at FROM leads WHERE listing_id = ? AND requester_id = ?
                  AND status IN ('NEW','CONTACTED','APPOINTED') ORDER BY created_at DESC, id DESC LIMIT 1
                """, listingId, requesterId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("listingAcceptsLeads", accepting);
        result.put("ownListing", ownerId.equals(requesterId));
        result.put("requesterKycVerified", isKycVerified(requesterId));
        result.put("ownerKycVerified", isKycVerified(ownerId));
        result.put("openLeadId", open.isEmpty() ? null : open.get(0).get("id"));
        result.put("openLeadStatus", open.isEmpty() ? null : open.get(0).get("status"));
        return result;
    }

    /**
     * F17.4: the requester hit the KYC wall of the lead API. Called by the controller after {@link #submit} was refused
     * (its transaction is already rolled back), in a transaction of its own; one event per requester, listing and day
     * (Vietnam time), so retries do not inflate the funnel.
     */
    @Transactional
    public void recordKycBlocked(UUID requesterId, UUID listingId) {
        String day = java.time.LocalDate.ofInstant(clock.instant(), java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        analytics.recordServer("lead_kyc_blocked", 1, requesterId + ":" + listingId + ":" + day, requesterId, listingId,
                Map.of("context", "lead_form"));
    }

    private void requireVerifiedKyc(UUID userId, String code, String message) {
        if (!isKycVerified(userId)) throw ApiException.conflict(code, message);
    }

    /** VERIFIED and not expired (contract §6: identity VERIFIED = status VERIFIED and expires_at null or future). */
    private boolean isKycVerified(UUID userId) {
        Boolean verified = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_kyc_profiles k JOIN users u ON u.id = k.user_id
                               WHERE k.user_id = ? AND u.status = 'ACTIVE' AND k.status = 'VERIFIED'
                               AND (k.expires_at IS NULL OR k.expires_at > ?))
                """, Boolean.class, userId, Timestamp.from(clock.instant()));
        return Boolean.TRUE.equals(verified);
    }

    /** Lấy toàn bộ Lead cho Bàn điều phối / Quản trị viên. */
    @Transactional(readOnly = true)
    public List<Lead> getAllLeads(int page, int size) {
        return leadPersistencePort.findPage(page, size);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Listing> getListingContexts(Collection<Lead> leads) {
        List<UUID> ids = leads.stream().map(Lead::getListingId).distinct().toList();
        return listingPersistencePort.findByIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Listing::getId, listing -> listing));
    }

    @Transactional(readOnly = true)
    public String revealPhone(UUID leadId, UUID actorId, boolean privileged) {
        LeadAccessService.LeadAccess lead = access.requireOwnerSide(leadId, actorId, privileged);
        Lead full = leadPersistencePort.findById(lead.leadId())
                .orElseThrow(() -> ApiException.notFound("LEAD_NOT_FOUND", "Không tìm thấy yêu cầu liên hệ."));
        if (!full.isConsentPolicy()) throw ApiException.conflict("NO_CONSENT", "Khách chưa đồng ý chia sẻ thông tin liên hệ.");
        if (full.getStatus() == LeadStatus.WITHDRAWN) {
            throw ApiException.conflict("LEAD_WITHDRAWN", "Khách đã rút yêu cầu nên không thể xem số liên hệ.");
        }
        return piiProtection.reveal(full.getPhoneEncrypted());
    }
}
