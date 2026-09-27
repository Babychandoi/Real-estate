package com.company.bds.testsupport;

import com.company.bds.iam.application.AuthService;
import com.company.bds.shared.security.PiiProtectionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builders that insert self-contained rows (random ids, unique emails/slugs) directly with SQL, so tests do not depend on
 * pre-existing data or on the write paths other streams are changing. Every builder returns what the test needs to
 * reference the rows afterwards.
 */
public final class TestData {
    public static final String DEFAULT_PASSWORD = "Strong-Test-Password-2026!";
    /** Low BCrypt cost keeps fixtures fast; verification reads the cost from the hash, so login still works. */
    private static final PasswordEncoder FAST_ENCODER = new BCryptPasswordEncoder(4);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    private final JdbcTemplate jdbc;
    private final PiiProtectionService pii;

    public TestData(JdbcTemplate jdbc, PiiProtectionService pii) {
        this.jdbc = jdbc;
        this.pii = pii;
    }

    public UserBuilder user() { return new UserBuilder(); }

    public ListingBuilder listing(UUID ownerId) { return new ListingBuilder(ownerId); }

    public LeadBuilder lead(UUID listingId) { return new LeadBuilder(listingId); }

    /** Issues a bearer token for the user exactly like a successful login would (session row with a hashed token). */
    public String sessionFor(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO auth_sessions(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)",
                UUID.randomUUID(), userId, AuthService.sha256(token), Timestamp.from(Instant.now().plus(Duration.ofHours(1))));
        return token;
    }

    /** Ensures a user row exists for a fixed id used by {@code @WithMockUser} tests (idempotent). */
    public void ensureUser(UUID id, String role) {
        jdbc.update("""
                INSERT INTO users(id,phone_lookup_hash,phone_encrypted,full_name,email,password_hash,status)
                VALUES (?,?,?,?,?,NULL,'ACTIVE') ON CONFLICT (id) DO NOTHING
                """, id, AuthService.sha256("it-user:" + id), "NOT_PROVIDED", "Người dùng kiểm thử", "it-" + id + "@example.test");
        jdbc.update("INSERT INTO user_roles(user_id,role) VALUES (?,?) ON CONFLICT DO NOTHING", id, role);
    }

    public record TestUser(UUID id, String email, String password, String role, String name) {}

    public record TestListing(UUID id, String slug, UUID publicRevisionId, UUID latestRevisionId, List<UUID> revisionIds,
                              List<String> mediaUrls) {}

    public final class UserBuilder {
        private String role = "USER";
        private String kycStatus;
        private String email;
        private String password = DEFAULT_PASSWORD;
        private String status = "ACTIVE";
        private String name = "Người dùng kiểm thử";
        private String planCode = "FREE";
        private int quota = 2;

        private UserBuilder() {}

        public UserBuilder role(String value) { role = Objects.requireNonNull(value); return this; }
        /** {@code VERIFIED}, {@code PENDING} or {@code REJECTED}; {@code null} means no KYC profile. */
        public UserBuilder kyc(String value) { kycStatus = value; return this; }
        public UserBuilder verifiedKyc() { return kyc("VERIFIED"); }
        public UserBuilder email(String value) { email = value; return this; }
        public UserBuilder password(String value) { password = value; return this; }
        public UserBuilder status(String value) { status = value; return this; }
        public UserBuilder name(String value) { name = value; return this; }
        public UserBuilder plan(String code, int remainingQuota) { planCode = code; quota = remainingQuota; return this; }

        public TestUser create() {
            UUID id = UUID.randomUUID();
            String mail = email != null ? email : "it-" + id + "@example.test";
            jdbc.update("""
                    INSERT INTO users(id,phone_lookup_hash,phone_encrypted,full_name,email,password_hash,status,email_verified_at,
                                      plan_code,listing_quota_remaining)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, id, AuthService.sha256("it-user:" + id), "NOT_PROVIDED", name, mail,
                    password == null ? null : FAST_ENCODER.encode(password), status,
                    "PENDING_EMAIL_VERIFICATION".equals(status) ? null : Timestamp.from(Instant.now()), planCode, quota);
            jdbc.update("INSERT INTO user_roles(user_id,role) VALUES (?,?)", id, role);
            if (kycStatus != null) {
                jdbc.update("""
                        INSERT INTO user_kyc_profiles(id,user_id,id_number_encrypted,id_number_lookup_hash,full_name,status,created_at,verified_at)
                        VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,?)
                        """, UUID.randomUUID(), id, "v1:001****" + String.format("%04d", Math.floorMod(id.hashCode(), 10_000)) + ":test:test",
                        AuthService.sha256("it-kyc:" + id), name, kycStatus,
                        "VERIFIED".equals(kycStatus) ? Timestamp.from(Instant.now()) : null);
            }
            return new TestUser(id, mail, password, role, name);
        }
    }

    public final class ListingBuilder {
        private final UUID ownerId;
        private String status = "ACTIVE";
        private String purpose = "SALE";
        private String propertyType = "APARTMENT";
        private long priceVnd = 3_000_000_000L;
        private BigDecimal areaM2 = new BigDecimal("70.00");
        private String title = "Căn hộ kiểm thử tích hợp";
        private String districtCode = "005";
        private String addressSummary = "Cầu Giấy, Hà Nội";
        private Double latitude;
        private Double longitude;
        private Integer bedrooms = 2;
        private String legalStatus;
        private int mediaCount = 1;
        private List<String> mediaUrls;
        private int olderApprovedRevisions;
        private Instant createdAt = Instant.now();

        private ListingBuilder(UUID ownerId) { this.ownerId = Objects.requireNonNull(ownerId); }

        /** DRAFT, PENDING_REVIEW, ACTIVE, PAUSED, EXPIRED, REJECTED or LOCKED (ACTIVE by default, with an APPROVED public revision). */
        public ListingBuilder status(String value) { status = value; return this; }
        public ListingBuilder purpose(String value) { purpose = value; return this; }
        public ListingBuilder propertyType(String value) { propertyType = value; return this; }
        public ListingBuilder price(long value) { priceVnd = value; return this; }
        public ListingBuilder area(String value) { areaM2 = new BigDecimal(value); return this; }
        public ListingBuilder title(String value) { title = value; return this; }
        public ListingBuilder district(String code, String address) { districtCode = code; addressSummary = address; return this; }
        public ListingBuilder location(double lat, double lng) { latitude = lat; longitude = lng; return this; }
        public ListingBuilder bedrooms(Integer value) { bedrooms = value; return this; }
        public ListingBuilder legalStatus(String value) { legalStatus = value; return this; }
        public ListingBuilder media(int count) { mediaCount = count; mediaUrls = null; return this; }
        public ListingBuilder mediaUrls(List<String> urls) { mediaUrls = List.copyOf(urls); return this; }
        /** Adds older APPROVED revisions before the current one (history depth for query-count tests). */
        public ListingBuilder olderRevisions(int count) { olderApprovedRevisions = count; return this; }
        public ListingBuilder createdAt(Instant value) { createdAt = value; return this; }

        public TestListing create() {
            UUID listingId = UUID.randomUUID();
            String slug = "it-" + listingId;
            jdbc.update("INSERT INTO listings(id,owner_id,status,is_verified_owner,version,slug,created_at,updated_at) VALUES (?,?,?,FALSE,0,?,?,?)",
                    listingId, ownerId, status, slug, Timestamp.from(createdAt), Timestamp.from(createdAt));
            List<String> urls = mediaUrls != null ? mediaUrls : defaultMedia(listingId, mediaCount);
            List<UUID> revisions = new ArrayList<>();
            int number = 1;
            for (int i = 0; i < olderApprovedRevisions; i++, number++) {
                revisions.add(insertRevision(listingId, number, "APPROVED", priceVnd + (olderApprovedRevisions - i) * 10_000_000L, urls));
            }
            String latestStatus = switch (status) {
                case "ACTIVE", "PAUSED", "EXPIRED", "LOCKED" -> "APPROVED";
                case "PENDING_REVIEW" -> "SUBMITTED";
                case "REJECTED" -> "REJECTED";
                case "DRAFT" -> "DRAFT";
                default -> throw new IllegalArgumentException("Unknown listing status " + status);
            };
            UUID latest = insertRevision(listingId, number, latestStatus, priceVnd, urls);
            revisions.add(latest);
            UUID publicRevision = "APPROVED".equals(latestStatus) ? latest : null;
            if (publicRevision != null) jdbc.update("UPDATE listings SET public_revision_id=? WHERE id=?", publicRevision, listingId);
            return new TestListing(listingId, slug, publicRevision, latest, List.copyOf(revisions), urls);
        }

        private UUID insertRevision(UUID listingId, int number, String revisionStatus, long price, List<String> urls) {
            UUID revisionId = UUID.randomUUID();
            Timestamp created = Timestamp.from(createdAt);
            boolean submitted = !"DRAFT".equals(revisionStatus);
            boolean moderated = "APPROVED".equals(revisionStatus) || "REJECTED".equals(revisionStatus);
            jdbc.update("""
                    INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,
                        description,province_code,district_code,address_summary,public_latitude,public_longitude,created_at,
                        submitted_at,moderated_at,bedrooms,legal_status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'01',?,?,?,?,?,?,?,?,?)
                    """, revisionId, listingId, number, revisionStatus, title, purpose, propertyType, price, areaM2,
                    "Mô tả tin kiểm thử tích hợp.", districtCode, addressSummary, latitude, longitude, created,
                    submitted ? created : null, moderated ? created : null, bedrooms, legalStatus);
            for (int i = 0; i < urls.size(); i++) {
                jdbc.update("INSERT INTO listing_media(id,revision_id,media_url,is_primary,sort_order,created_at) VALUES (?,?,?,?,?,?)",
                        UUID.randomUUID(), revisionId, urls.get(i), i == 0, i, created);
            }
            return revisionId;
        }
    }

    public final class LeadBuilder {
        private final UUID listingId;
        private UUID requesterId;
        private String status = "NEW";
        private String requestType = "CONSULTATION";
        private String fullName = "Khách kiểm thử";
        private String phone;
        private Instant createdAt = Instant.now();

        private LeadBuilder(UUID listingId) { this.listingId = Objects.requireNonNull(listingId); }

        public LeadBuilder requester(UUID value) { requesterId = value; return this; }
        public LeadBuilder status(String value) { status = value; return this; }
        public LeadBuilder requestType(String value) { requestType = value; return this; }
        public LeadBuilder fullName(String value) { fullName = value; return this; }
        public LeadBuilder phone(String value) { phone = value; return this; }
        public LeadBuilder createdAt(Instant value) { createdAt = value; return this; }

        public UUID create() {
            UUID id = UUID.randomUUID();
            String rawPhone = phone != null ? phone : String.format("0998%06d", Math.floorMod(PHONE_SEQUENCE.incrementAndGet() * 7919 + id.hashCode(), 1_000_000));
            PiiProtectionService.ProtectedValue protectedPhone = pii.protect(rawPhone);
            jdbc.update("""
                    INSERT INTO leads(id,listing_id,full_name,phone_encrypted,phone_lookup_hash,note,consent_policy,status,created_at,request_type,requester_id)
                    VALUES (?,?,?,?,?,?,TRUE,?,?,?,?)
                    """, id, listingId, fullName, protectedPhone.encrypted(), protectedPhone.blindIndex(), "Ghi chú kiểm thử", status,
                    Timestamp.from(createdAt), requestType, requesterId);
            return id;
        }
    }

    private static List<String> defaultMedia(UUID listingId, int count) {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            urls.add("https://images.unsplash.com/photo-it-" + listingId.toString().substring(0, 8) + "-" + i);
        }
        return urls;
    }
}
