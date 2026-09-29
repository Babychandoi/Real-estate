package com.company.bds.shared.uat;

import com.company.bds.shared.security.PiiProtectionService;
import com.company.bds.shared.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * One-off UAT data seeder so every screen has realistic content to review.
 *
 * <p>Runs only when {@code app.uat-seed.mode} is {@code seed} or {@code purge}. Every synthetic row uses an id
 * starting with {@code ee5eed} (text keys start with {@code UAT}), so {@code purge} removes exactly what
 * {@code seed} created. Existing accounts are only referenced (as owners/requesters), never modified; the one
 * exception is a synthetic VERIFIED KYC profile added (and purged) for accounts listed in
 * {@code app.uat-seed.kyc-verified-accounts} that have no profile at all.
 *
 * <p>{@code app.uat-seed.clock=<ISO instant>} makes every timestamp relative to that instant, so E2E fixtures are
 * identical run after run (e.g. {@code --app.uat-seed.clock=2026-09-01T03:00:00Z}). Outside production,
 * {@code app.uat-seed.password} gives the synthetic accounts a password so E2E can sign in as a buyer, broker or owner
 * (their e-mails are {@code uat.<name>@example.invalid}).
 */
@Component
@ConditionalOnProperty(name = "app.uat-seed.mode")
public class UatDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(UatDataSeeder.class);
    private static final String ID_PREFIX = "ee5eed";
    private static final String SAMPLE_NOTE = "\n\n(Tin mẫu phục vụ kiểm thử giao diện.)";

    private static final int K_USER = 0x01, K_LISTING = 0x02, K_REVISION = 0x03, K_MEDIA = 0x04, K_LEAD = 0x05,
            K_REPORT = 0x06, K_KYC = 0x07, K_VERIFICATION = 0x08, K_PROJECT = 0x09, K_ARTICLE = 0x0a,
            K_ARTICLE_REV = 0x0b, K_ORDER = 0x0c, K_INVOICE = 0x0d;
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final JdbcTemplate jdbc;
    private final PiiProtectionService pii;
    private final TransactionTemplate tx;
    private final ApplicationContext context;
    private final String mode;
    private final boolean exitAfterRun;
    private final List<String> accountEmails;
    private final String elasticsearchBase;
    private final String searchIndex;
    private final String passwordHash;
    private final Instant now;
    private Random random;
    private List<String> kycVerifiedAccounts = List.of();

    public UatDataSeeder(JdbcTemplate jdbc, PiiProtectionService pii, TransactionTemplate tx, ApplicationContext context,
                         @Value("${app.uat-seed.mode}") String mode,
                         @Value("${app.uat-seed.exit:true}") boolean exitAfterRun,
                         @Value("${app.uat-seed.accounts:phonglop7d@gmail.com,phong01012k2@gmail.com,moderator.test@nhadatchuan.online,phongdq@weconex.vn}") List<String> accountEmails,
                         @Value("${spring.elasticsearch.uris:http://localhost:9200}") String elasticsearchUris,
                         @Value("${app.search.index-name:bds-listings}") String searchIndex,
                         @Value("${app.uat-seed.clock:}") String clock,
                         @Value("${app.mode:demo}") String appMode,
                         @Value("${app.uat-seed.password:}") String password) {
        this.jdbc = jdbc;
        this.pii = pii;
        this.tx = tx;
        this.context = context;
        this.mode = mode.trim().toLowerCase(Locale.ROOT);
        this.exitAfterRun = exitAfterRun;
        this.accountEmails = accountEmails.stream().map(email -> email.trim().toLowerCase(Locale.ROOT)).filter(email -> !email.isBlank()).toList();
        this.elasticsearchBase = elasticsearchUris.split(",")[0].replaceAll("/+$", "");
        this.searchIndex = searchIndex;
        this.now = parseClock(clock);
        this.passwordHash = hashPassword(password, appMode);
    }

    /**
     * {@code app.uat-seed.kyc-verified-accounts}: real accounts (from {@code app.uat-seed.accounts}) that get a
     * synthetic VERIFIED KYC profile when they have none, so E2E can post listings as {@code demo.broker} on a fresh
     * database (V018 ran before the demo accounts existed). An existing profile, whatever its status, is left alone;
     * the synthetic profile has an {@code ee5eed} id and is removed by purge.
     */
    @Value("${app.uat-seed.kyc-verified-accounts:}")
    public void setKycVerifiedAccounts(List<String> emails) {
        this.kycVerifiedAccounts = emails == null ? List.of()
                : emails.stream().map(email -> email.trim().toLowerCase(Locale.ROOT)).filter(email -> !email.isBlank()).toList();
    }

    private static Instant parseClock(String clock) {
        if (clock == null || clock.isBlank()) return Instant.now();
        try {
            return Instant.parse(clock.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("app.uat-seed.clock must be an ISO-8601 instant such as 2026-09-01T03:00:00Z", ex);
        }
    }

    private static String hashPassword(String password, String appMode) {
        if (password == null || password.isBlank()) return null;
        if ("production".equalsIgnoreCase(appMode)) {
            throw new IllegalStateException("app.uat-seed.password is refused in production: synthetic accounts must not be able to sign in");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("app.uat-seed.password must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        return new BCryptPasswordEncoder(10).encode(password);
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            switch (mode) {
                case "seed" -> seedNow();
                case "purge" -> purgeNow();
                default -> throw new IllegalArgumentException("app.uat-seed.mode must be 'seed' or 'purge'");
            }
        } catch (RuntimeException exception) {
            log.error("UAT seed failed: {}", exception.getMessage(), exception);
            exitCode = 1;
        }
        if (exitAfterRun) {
            int code = exitCode;
            System.exit(SpringApplication.exit(context, () -> code));
        }
    }

    /** Replaces every synthetic row with a fresh data set; identical for a given clock and database state. */
    public void seedNow() {
        random = new Random(20260926L);
        tx.executeWithoutResult(status -> purgeRows());
        tx.executeWithoutResult(status -> seed());
    }

    /** Removes every synthetic row (and its search documents); real accounts and their own data stay untouched. */
    public void purgeNow() {
        List<String> listingIds = jdbc.queryForList("SELECT id::text FROM listings WHERE id IN " + SYNTHETIC_LISTINGS, String.class);
        tx.executeWithoutResult(status -> purgeRows());
        listingIds.forEach(this::deleteFromSearchIndex);
        log.info("UAT seed: purged all synthetic rows ({} listings)", listingIds.size());
    }

    // ------------------------------------------------------------------ purge

    /**
     * Every synthetic id has the shape {@code ee5eed<kind>-0000-4000-8000-<n>} (see {@link #id}); a random UUID matches it
     * with probability about 2^-66, so a real row is never mistaken for a synthetic one (a bare "ee5eed" prefix would be).
     */
    static final String SYNTHETIC_ID_PATTERN = "^ee5eed[0-9a-f]{2}-0000-4000-8000-[0-9a-f]{12}$";
    private static final String SYNTHETIC_USERS = "(SELECT id FROM users WHERE " + synthetic("id") + ")";
    /** Seeded listings plus every listing a synthetic account created later through the API. */
    private static final String SYNTHETIC_LISTINGS = "(SELECT id FROM listings WHERE " + synthetic("id") + " OR owner_id IN " + SYNTHETIC_USERS + ")";
    private static final String SYNTHETIC_LEADS = "(SELECT id FROM leads WHERE " + synthetic("id") + " OR listing_id IN " + SYNTHETIC_LISTINGS
            + " OR requester_id IN " + SYNTHETIC_USERS + ")";
    private static final String SYNTHETIC_CONTRACTS = "(SELECT id FROM deposit_contracts WHERE listing_id IN " + SYNTHETIC_LISTINGS
            + " OR buyer_id IN " + SYNTHETIC_USERS + " OR seller_id IN " + SYNTHETIC_USERS + ")";
    private static final String SYNTHETIC_ORDERS = "(SELECT id FROM package_orders WHERE " + synthetic("id") + " OR user_id IN " + SYNTHETIC_USERS + ")";
    /** Per-user rows removed with the synthetic accounts (sessions and tokens created by E2E sign-ins included). */
    private static final List<String> USER_OWNED_TABLES = List.of("auth_sessions", "email_verification_tokens", "password_reset_tokens",
            "kyc_document_access_grants", "user_notifications", "broker_sla_settings", "media_objects:owner_id");

    private static String synthetic(String column) {
        return column + "::text ~ '" + SYNTHETIC_ID_PATTERN + "'";
    }

    /**
     * Removes the seeded rows and everything synthetic accounts created afterwards through the API (listings with their
     * revisions/media, leads, orders/invoices, contracts, sessions, tokens, notifications, uploads, analytics, jobs), in
     * foreign-key order so neither purge nor a re-seed aborts. Real rows are only touched where they point at synthetic data:
     * leads/contracts on synthetic listings are removed, links to synthetic projects/reviewers are cleared. The append-only
     * audit trail is left as is.
     */
    private void purgeRows() {
        jdbc.update("DELETE FROM analytics_events WHERE listing_id IN " + SYNTHETIC_LISTINGS + " OR user_id IN " + SYNTHETIC_USERS
                + " OR " + synthetic("listing_id") + " OR " + synthetic("user_id"));
        jdbc.update("DELETE FROM background_jobs WHERE dedupe_key ~ 'ee5eed[0-9a-f]{2}-0000-4000-8000-[0-9a-f]{12}'");
        jdbc.update("DELETE FROM api_idempotency_keys WHERE resource_id IN " + SYNTHETIC_LEADS);
        jdbc.update("DELETE FROM escrow_transactions WHERE contract_id IN " + SYNTHETIC_CONTRACTS + " OR performed_by IN " + SYNTHETIC_USERS);
        jdbc.update("DELETE FROM deposit_contracts WHERE id IN " + SYNTHETIC_CONTRACTS);
        jdbc.update("DELETE FROM invoices WHERE " + synthetic("id") + " OR order_id IN " + SYNTHETIC_ORDERS + " OR user_id IN " + SYNTHETIC_USERS);
        jdbc.update("UPDATE package_orders SET reviewed_by = NULL WHERE reviewed_by IN " + SYNTHETIC_USERS);
        jdbc.update("DELETE FROM package_orders WHERE id IN " + SYNTHETIC_ORDERS);
        jdbc.update("DELETE FROM leads WHERE id IN " + SYNTHETIC_LEADS);
        jdbc.update("DELETE FROM listing_reports WHERE " + synthetic("id") + " OR listing_id IN " + SYNTHETIC_LISTINGS);
        jdbc.update("DELETE FROM listing_verifications WHERE " + synthetic("id") + " OR listing_id IN " + SYNTHETIC_LISTINGS);
        jdbc.update("UPDATE listing_verifications SET decided_by = NULL WHERE decided_by IN " + SYNTHETIC_USERS);
        jdbc.update("UPDATE listings SET public_revision_id = NULL WHERE id IN " + SYNTHETIC_LISTINGS);
        jdbc.update("DELETE FROM listing_revisions WHERE listing_id IN " + SYNTHETIC_LISTINGS); // listing_media cascades
        jdbc.update("DELETE FROM listings WHERE id IN " + SYNTHETIC_LISTINGS);
        // A real revision may point at a synthetic project (linked during UAT); unlink it so the project can be removed.
        jdbc.update("UPDATE listing_revisions SET project_id = NULL WHERE project_id IN (SELECT id FROM projects WHERE " + synthetic("id") + ")");
        jdbc.update("DELETE FROM cms_article_revisions WHERE article_id IN (SELECT id FROM cms_articles WHERE " + synthetic("id") + ")");
        jdbc.update("DELETE FROM cms_articles WHERE " + synthetic("id"));
        jdbc.update("DELETE FROM projects WHERE " + synthetic("id"));
        jdbc.update("DELETE FROM user_kyc_profiles WHERE " + synthetic("id") + " OR user_id IN " + SYNTHETIC_USERS);
        for (String table : USER_OWNED_TABLES) {
            String[] parts = table.split(":");
            String column = parts.length > 1 ? parts[1] : "user_id";
            jdbc.update("DELETE FROM " + parts[0] + " WHERE " + column + " IN " + SYNTHETIC_USERS);
        }
        jdbc.update("DELETE FROM user_roles WHERE user_id IN " + SYNTHETIC_USERS);
        jdbc.update("DELETE FROM users WHERE " + synthetic("id"));
        // The read-model triggers (V034, deferred to commit) enqueue search-index jobs for the rows deleted above: fire
        // them now and drop those jobs too, so nothing synthetic is left behind.
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        jdbc.update("DELETE FROM background_jobs WHERE dedupe_key ~ 'ee5eed[0-9a-f]{2}-0000-4000-8000-[0-9a-f]{12}'");
    }

    private void deleteFromSearchIndex(String listingId) {
        try {
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(elasticsearchBase + "/" + searchIndex + "/_doc/" + listingId))
                    .timeout(Duration.ofSeconds(3)).DELETE().build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // Search hydration only returns ACTIVE listings from PostgreSQL, so a stale document is harmless.
        }
    }

    // ------------------------------------------------------------------ seed

    private record Person(UUID id, String name, String role, boolean real, boolean kycVerified, UUID kycId) {}
    private record Place(String district, String districtCode, double lat, double lng, String[] streets, String[] wards) {}
    private record SeededListing(UUID id, Person owner, String status, String title, String purpose, long price) {}

    private static final Place[] PLACES = {
            new Place("Cầu Giấy", "005", 21.0310, 105.7950, new String[]{"Trần Duy Hưng", "Nguyễn Khang", "Xuân Thủy", "Duy Tân"}, new String[]{"Dịch Vọng", "Trung Hòa", "Yên Hòa"}),
            new Place("Ba Đình", "001", 21.0340, 105.8190, new String[]{"Kim Mã", "Đội Cấn", "Liễu Giai", "Văn Cao"}, new String[]{"Ngọc Khánh", "Cống Vị", "Liễu Giai"}),
            new Place("Hoàn Kiếm", "002", 21.0270, 105.8520, new String[]{"Hàng Bài", "Lý Thường Kiệt", "Hàng Bạc", "Phan Chu Trinh"}, new String[]{"Tràng Tiền", "Hàng Bạc", "Phan Chu Trinh"}),
            new Place("Tây Hồ", "003", 21.0680, 105.8190, new String[]{"Âu Cơ", "Xuân Diệu", "Lạc Long Quân", "Tô Ngọc Vân"}, new String[]{"Quảng An", "Nhật Tân", "Xuân La"}),
            new Place("Đống Đa", "006", 21.0160, 105.8260, new String[]{"Thái Hà", "Chùa Láng", "Tôn Đức Thắng", "Xã Đàn"}, new String[]{"Láng Hạ", "Trung Liệt", "Ô Chợ Dừa"}),
            new Place("Hai Bà Trưng", "007", 21.0060, 105.8580, new String[]{"Bạch Mai", "Minh Khai", "Lò Đúc", "Kim Ngưu"}, new String[]{"Thanh Nhàn", "Vĩnh Tuy", "Bách Khoa"}),
            new Place("Thanh Xuân", "009", 20.9950, 105.8110, new String[]{"Nguyễn Trãi", "Khương Đình", "Vũ Trọng Phụng", "Lê Văn Lương"}, new String[]{"Nhân Chính", "Khương Trung", "Thanh Xuân Bắc"}),
            new Place("Hoàng Mai", "008", 20.9760, 105.8500, new String[]{"Tam Trinh", "Giải Phóng", "Linh Đường", "Kim Giang"}, new String[]{"Hoàng Liệt", "Định Công", "Đại Kim"}),
            new Place("Long Biên", "004", 21.0420, 105.8880, new String[]{"Nguyễn Văn Cừ", "Ngọc Lâm", "Cổ Linh", "Nguyễn Sơn"}, new String[]{"Bồ Đề", "Phúc Đồng", "Thạch Bàn"}),
            new Place("Nam Từ Liêm", "019", 21.0170, 105.7680, new String[]{"Lê Đức Thọ", "Mễ Trì", "Hàm Nghi", "Đỗ Đức Dục"}, new String[]{"Mỹ Đình 1", "Mễ Trì", "Tây Mỗ"}),
            new Place("Bắc Từ Liêm", "021", 21.0690, 105.7680, new String[]{"Phạm Văn Đồng", "Hồ Tùng Mậu", "Cổ Nhuế", "Hoàng Quốc Việt"}, new String[]{"Cổ Nhuế 1", "Phú Diễn", "Xuân Đỉnh"}),
            new Place("Hà Đông", "268", 20.9660, 105.7720, new String[]{"Quang Trung", "Tố Hữu", "Nguyễn Văn Lộc", "Lê Trọng Tấn"}, new String[]{"Mộ Lao", "Văn Quán", "La Khê"}),
            new Place("Gia Lâm", "018", 21.0180, 105.9380, new String[]{"Ngô Xuân Quảng", "Cổ Bi", "Kiêu Kỵ"}, new String[]{"Trâu Quỳ", "Đa Tốn", "Cổ Bi"}),
            new Place("Đông Anh", "017", 21.1360, 105.8450, new String[]{"Cao Lỗ", "Võ Nguyên Giáp", "Uy Nỗ"}, new String[]{"Uy Nỗ", "Kim Chung", "Hải Bối"}),
    };

    private static final String[] DIRECTIONS = {"Đông", "Tây", "Nam", "Bắc", "Đông Nam", "Đông Bắc", "Tây Nam", "Tây Bắc"};
    private static final String[] APARTMENT_FURNISHING = {"FULL", "BASIC", "NONE"};

    /** Projects open for listings, by district code: apartments in those districts are linked to them. */
    private final Map<String, UUID> projectsByDistrict = new HashMap<>();

    private void seed() {
        List<Person> real = loadRealAccounts();
        List<String> imagePool = jdbc.queryForList("""
                SELECT url FROM (
                  SELECT '/api/v1/public/media/' || object_key AS url FROM media_objects
                   WHERE visibility='PUBLIC' AND content_type LIKE 'image/%'
                  UNION
                  SELECT media_url FROM listing_media WHERE media_url LIKE '/api/v1/public/media/%'
                ) images ORDER BY url""", String.class);

        List<Person> fakeBrokers = new ArrayList<>();
        List<Person> fakeBuyers = new ArrayList<>();
        List<Person> fakeOwners = new ArrayList<>();
        seedFakeUsers(fakeBrokers, fakeBuyers, fakeOwners);
        projectsByDistrict.clear();
        int projects = seedProjects();

        List<Person> realSellers = real.stream().filter(p -> Roles.isPoster(p.role())).toList();
        List<Person> realBuyers = real.stream().filter(p -> p.role().equals(Roles.USER)).toList();

        List<SeededListing> listings = new ArrayList<>();
        int n = 0;
        // Each real seller gets a full lifecycle mix so "Tin của tôi" and the workspace show every state.
        String[] lifecycle = {"ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "PENDING_REVIEW_EDIT", "PENDING_REVIEW", "DRAFT", "PAUSED", "REJECTED", "EXPIRED"};
        for (Person seller : realSellers) {
            for (String state : lifecycle) listings.add(seedListing(++n, seller, state, imagePool));
        }
        String[] fakeMix = {"ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "PENDING_REVIEW", "PENDING_REVIEW_EDIT", "ACTIVE"};
        for (Person seller : fakeBrokers) {
            for (String state : fakeMix) listings.add(seedListing(++n, seller, state, imagePool));
        }
        // Private owners post one or two own properties; the one whose KYC is pending has only a draft.
        String[][] ownerMixes = {{"ACTIVE", "ACTIVE", "PENDING_REVIEW_EDIT", "DRAFT"}, {"DRAFT"}};
        for (int i = 0; i < fakeOwners.size(); i++) {
            for (String state : ownerMixes[i % ownerMixes.length]) listings.add(seedListing(++n, fakeOwners.get(i), state, imagePool));
        }

        int leads = seedLeads(listings, realSellers, realBuyers, fakeBuyers);
        int reports = seedReports(listings);
        int verifications = seedVerifications(listings);
        int articles = seedArticles(imagePool);
        int orders = seedOrders(real, fakeBrokers);
        log.info("UAT seed done: clock={}, accounts found={} {}, fake users={} (owners={}), listings={}, leads={}, reports={}, verifications={}, projects={}, articles={}, orders={}, image pool={}",
                now, real.size(), real.stream().map(p -> p.name() + "/" + p.role()).toList(), fakeBrokers.size() + fakeBuyers.size() + fakeOwners.size(),
                fakeOwners.size(), listings.size(), leads, reports, verifications, projects, articles, orders, imagePool.size());
    }

    private void verifyListedRealAccounts() {
        for (int i = 0; i < kycVerifiedAccounts.size(); i++) {
            String email = kycVerifiedAccounts.get(i);
            if (!accountEmails.contains(email)) {
                log.warn("UAT seed: {} is in kyc-verified-accounts but not in accounts, skipped", email);
                continue;
            }
            List<Map<String, Object>> users = jdbc.queryForList(
                    "SELECT id, full_name FROM users WHERE LOWER(email)=? LIMIT 1", email);
            if (users.isEmpty()) continue;
            UUID userId = (UUID) users.get(0).get("id");
            String name = (String) users.get(0).get("full_name");
            jdbc.update("""
                    INSERT INTO user_kyc_profiles(id,user_id,id_number_encrypted,id_number_lookup_hash,full_name,dob,address,
                                                  face_match_score,status,created_at,verified_at,expires_at)
                    VALUES (?,?,?,?,?,?,?,?,'VERIFIED',?,?,?)
                    ON CONFLICT (user_id) DO NOTHING""",
                    id(K_KYC, 0x100 + i), userId, "v1:0099****" + String.format("%04d", i) + ":synthetic:synthetic",
                    sha256("uat-kyc-real-" + userId), name, "01/01/1990", "Hồ sơ kiểm thử tổng hợp", 0.95,
                    ago(Duration.ofDays(30)), ago(Duration.ofDays(29)),
                    Timestamp.from(now.minus(Duration.ofDays(29)).atZone(java.time.ZoneOffset.UTC).plusMonths(24).toInstant()));
        }
    }

    private List<Person> loadRealAccounts() {
        verifyListedRealAccounts();
        List<Person> people = new ArrayList<>();
        for (String email : accountEmails) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT u.id, u.full_name, %s AS role, k.id AS kyc_id, k.status AS kyc_status
                    FROM users u LEFT JOIN user_kyc_profiles k ON k.user_id=u.id
                    WHERE LOWER(u.email)=? LIMIT 1""".formatted(Roles.effectiveRoleSql("u.id")), email);
            if (rows.isEmpty()) {
                log.warn("UAT seed: account {} not found, skipped", email);
                continue;
            }
            Map<String, Object> row = rows.get(0);
            people.add(new Person((UUID) row.get("id"), (String) row.get("full_name"), (String) row.get("role"), true,
                    "VERIFIED".equals(row.get("kyc_status")), (UUID) row.get("kyc_id")));
        }
        return people;
    }

    private void seedFakeUsers(List<Person> brokers, List<Person> buyers, List<Person> owners) {
        String[][] people = {
                {"Nguyễn Minh Tuấn", "BROKER", "VERIFIED"}, {"Trần Thu Hà", "BROKER", "VERIFIED"}, {"Lê Quang Huy", "BROKER", "VERIFIED"},
                {"Phạm Ngọc Lan", "BROKER", "PENDING"}, {"Hoàng Đức Anh", "BROKER", "VERIFIED"},
                {"Vũ Thị Mai", "USER", "VERIFIED"}, {"Đặng Văn Long", "USER", "VERIFIED"}, {"Bùi Khánh Linh", "USER", "PENDING"},
                {"Đỗ Hoàng Nam", "USER", "PENDING"}, {"Ngô Thanh Thảo", "USER", "REJECTED"}, {"Dương Quốc Bảo", "USER", "VERIFIED"},
                {"Lý Hải Yến", "USER", "PENDING"}, {"Trịnh Công Sơn", "USER", null}, {"Mai Phương Anh", "USER", null},
                {"Phan Thanh Hải", "OWNER", "VERIFIED"}, {"Tạ Thu Hồng", "OWNER", "PENDING"},
        };
        for (int i = 0; i < people.length; i++) {
            String name = people[i][0], role = people[i][1], kyc = people[i][2];
            UUID id = id(K_USER, i + 1);
            PiiProtectionService.ProtectedValue phone = pii.protect(fakePhone(i + 1));
            String status = i == 12 ? "SUSPENDED" : i == 13 ? "PENDING_EMAIL_VERIFICATION" : "ACTIVE";
            Timestamp created = ago(Duration.ofDays(20 + (long) i * 9));
            jdbc.update("""
                    INSERT INTO users(id,phone_lookup_hash,phone_encrypted,full_name,email,password_hash,status,created_at,updated_at,
                                      email_verified_at,plan_code,plan_expires_at,listing_quota_remaining)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                    id, phone.blindIndex(), phone.encrypted(), name, "uat." + slugify(name).replace("-", ".") + "@example.invalid",
                    passwordHash, status, created, created, status.equals("PENDING_EMAIL_VERIFICATION") ? null : created,
                    role.equals("BROKER") ? (i % 2 == 0 ? "PRO" : "STANDARD") : "FREE",
                    role.equals("BROKER") ? Timestamp.from(now.plus(Duration.ofDays(12 + i))) : null,
                    role.equals("BROKER") ? 25 : role.equals("OWNER") ? 1 : 2);
            jdbc.update("INSERT INTO user_roles(user_id,role) VALUES (?,?)", id, role);
            UUID kycId = null;
            if (kyc != null) {
                kycId = id(K_KYC, i + 1);
                jdbc.update("""
                        INSERT INTO user_kyc_profiles(id,user_id,id_number_encrypted,id_number_lookup_hash,full_name,dob,address,
                                                      face_match_score,status,rejection_reason,created_at,verified_at,expires_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                        kycId, id, "v1:0012****" + String.format("%04d", 1000 + i) + ":synthetic:synthetic", sha256("uat-kyc-" + id),
                        name, String.format("%02d/%02d/%d", 1 + i % 27, 1 + i % 12, 1978 + i), "Số " + (10 + i) + " " + PLACES[i % PLACES.length].streets()[0] + ", " + PLACES[i % PLACES.length].district() + ", Hà Nội",
                        0.82 + (i % 5) * 0.03, kyc, kyc.equals("REJECTED") ? "Ảnh mặt trước CCCD bị lóa, không đọc được số giấy tờ." : null,
                        ago(Duration.ofDays(3 + i)), kyc.equals("VERIFIED") ? ago(Duration.ofDays(2 + i)) : null,
                        kyc.equals("VERIFIED") ? Timestamp.from(now.minus(Duration.ofDays(2 + i)).atZone(java.time.ZoneOffset.UTC).plusMonths(24).toInstant()) : null);
            }
            if (role.equals("BROKER")) {
                jdbc.update("INSERT INTO broker_sla_settings(user_id,first_response_minutes,reminder_enabled,daily_digest_enabled,updated_at) VALUES (?,?,?,?,?)",
                        id, 15 + i * 5, true, i % 2 == 0, created);
            }
            Person person = new Person(id, name, role, false, "VERIFIED".equals(kyc), kycId);
            switch (role) {
                case "BROKER" -> brokers.add(person);
                case "OWNER" -> owners.add(person);
                default -> buyers.add(person);
            }
        }
    }

    private SeededListing seedListing(int n, Person owner, String state, List<String> images) {
        Place place = PLACES[(n * 5 + 3) % PLACES.length];
        String street = place.streets()[n % place.streets().length];
        String ward = place.wards()[n % place.wards().length];
        String purpose = n % 4 == 0 ? "RENT" : "SALE";
        String type = switch (n % 7) { case 0, 3, 5 -> "APARTMENT"; case 1, 6 -> "HOUSE"; case 2 -> "TOWNHOUSE"; default -> n % 2 == 0 ? "LAND" : "VILLA"; };
        if (purpose.equals("RENT") && type.equals("LAND")) type = "APARTMENT";

        Integer bedrooms = null, bathrooms = null, floors = null;
        Double frontage = null, road = null;
        double area;
        long price;
        String title, legal;
        switch (type) {
            case "APARTMENT" -> {
                bedrooms = 1 + random.nextInt(4);
                bathrooms = Math.max(1, bedrooms - random.nextInt(2));
                area = 45 + bedrooms * 18 + random.nextInt(15);
                price = purpose.equals("SALE") ? roundTo(area * (48 + random.nextInt(45)) * 1_000_000L, 10_000_000L) : roundTo(6_000_000L + bedrooms * 4_500_000L + random.nextInt(8) * 1_000_000L, 500_000L);
                title = String.format("Căn hộ %dPN %s m² view thoáng, gần %s", bedrooms, trim(area), street);
                legal = purpose.equals("SALE") ? (n % 3 == 0 ? "Hợp đồng mua bán" : "Sổ hồng lâu dài") : null;
            }
            case "HOUSE" -> {
                floors = 3 + random.nextInt(4);
                bedrooms = floors; bathrooms = floors;
                area = 30 + random.nextInt(40);
                frontage = 3.2 + random.nextInt(25) / 10.0;
                road = 2.0 + random.nextInt(5);
                price = purpose.equals("SALE") ? roundTo(area * (120 + random.nextInt(130)) * 1_000_000L, 50_000_000L) : roundTo(12_000_000L + floors * 3_000_000L + random.nextInt(10) * 1_000_000L, 500_000L);
                title = String.format("Nhà riêng %d tầng ngõ %s, %s, ô tô đỗ cửa", floors, street, ward);
                legal = "Sổ đỏ chính chủ";
            }
            case "TOWNHOUSE" -> {
                floors = 4 + random.nextInt(4);
                bedrooms = floors - 1; bathrooms = floors;
                area = 55 + random.nextInt(60);
                frontage = 5 + random.nextInt(40) / 10.0;
                road = 12.0 + random.nextInt(20);
                price = purpose.equals("SALE") ? roundTo(area * (220 + random.nextInt(200)) * 1_000_000L, 100_000_000L) : roundTo(35_000_000L + random.nextInt(60) * 1_000_000L, 1_000_000L);
                title = String.format("Nhà phố mặt đường %s %s m², kinh doanh sầm uất", street, trim(area));
                legal = "Sổ đỏ chính chủ";
            }
            case "VILLA" -> {
                floors = 3; bedrooms = 4 + random.nextInt(3); bathrooms = bedrooms + 1;
                area = 160 + random.nextInt(200);
                frontage = 10.0 + random.nextInt(8);
                road = 13.0 + random.nextInt(10);
                price = purpose.equals("SALE") ? roundTo(area * (110 + random.nextInt(90)) * 1_000_000L, 100_000_000L) : roundTo(45_000_000L + random.nextInt(80) * 1_000_000L, 1_000_000L);
                title = String.format("Biệt thự song lập %s m² sân vườn, khu %s", trim(area), ward);
                legal = "Sổ hồng lâu dài";
            }
            default -> {
                area = 60 + random.nextInt(150);
                frontage = 4 + random.nextInt(60) / 10.0;
                road = 3.0 + random.nextInt(6);
                price = roundTo(area * (35 + random.nextInt(90)) * 1_000_000L, 10_000_000L);
                title = String.format("Đất thổ cư %s m² %s, %s, đường ô tô", trim(area), street, place.district());
                legal = "Sổ đỏ chính chủ";
            }
        }
        String direction = type.equals("APARTMENT") && n % 3 == 0 ? "Ban công " + DIRECTIONS[n % 8] : DIRECTIONS[(n * 3) % 8];
        String address = ward + ", " + place.district() + ", Hà Nội";
        double lat = place.lat() + (random.nextDouble() - 0.5) * 0.024;
        double lng = place.lng() + (random.nextDouble() - 0.5) * 0.024;
        String description = describe(type, purpose, street, ward, place.district(), bedrooms, floors, road) + SAMPLE_NOTE;

        String listingStatus = switch (state) { case "PENDING_REVIEW_EDIT" -> "PENDING_REVIEW"; default -> state; };
        Duration age = Duration.ofDays(1 + (n * 7L) % 45).plusHours(n % 24);
        UUID listingId = id(K_LISTING, n);
        boolean verifiedOwner = owner.kycVerified() && n % 3 != 0 && (listingStatus.equals("ACTIVE") || listingStatus.equals("PAUSED"));
        boolean live = state.equals("ACTIVE") || state.equals("PAUSED") || state.equals("PENDING_REVIEW_EDIT");
        // Last availability confirmation: between creation and the last update, so some live listings are stale (> 14 days).
        Timestamp confirmed = live ? ago(age.dividedBy(1 + n % 3)) : state.equals("EXPIRED") ? ago(age) : null;
        // Synthetic ACTIVE fixtures outlive CI dates; this is not the product listing expiry policy.
        Timestamp expires = state.equals("ACTIVE") || state.equals("PENDING_REVIEW_EDIT") ? Timestamp.from(now.plus(Duration.ofDays(3650 + n % 40)))
                : state.equals("EXPIRED") ? ago(age.dividedBy(2)) : null;
        jdbc.update("""
                INSERT INTO listings(id,owner_id,status,is_verified_owner,version,slug,created_at,updated_at,availability_confirmed_at,expires_at,source)
                VALUES (?,?,?,?,0,?,?,?,?,?,'SEED')""",
                listingId, owner.id(), listingStatus, verifiedOwner, slugify(title) + "-" + (2600 + n), ago(age), ago(age.dividedBy(3)), confirmed, expires);

        List<String> media = pickImages(images, n);
        UUID publicRevision = null;
        switch (state) {
            case "ACTIVE", "PAUSED", "EXPIRED" -> {
                if (n % 5 == 0) {
                    // A listing edited once after publication: revision history 1 → 2 with a price change.
                    insertRevision(id(K_REVISION, n * 10 + 1), listingId, 1, "APPROVED", title, purpose, type, roundTo(price * 108 / 100, 10_000_000L), area, description, place, address, lat, lng, bedrooms, bathrooms, floors, frontage, road, direction, legal, age, media, n * 10 + 1);
                    publicRevision = id(K_REVISION, n * 10 + 2);
                    insertRevision(publicRevision, listingId, 2, "APPROVED", title, purpose, type, price, area, description, place, address, lat, lng, bedrooms, bathrooms, floors, frontage, road, direction, legal, age.dividedBy(2), media, n * 10 + 2);
                } else {
                    publicRevision = id(K_REVISION, n * 10 + 1);
                    insertRevision(publicRevision, listingId, 1, "APPROVED", title, purpose, type, price, area, description, place, address, lat, lng, bedrooms, bathrooms, floors, frontage, road, direction, legal, age, media, n * 10 + 1);
                }
            }
            case "PENDING_REVIEW_EDIT" -> {
                // Live listing with a submitted edit: the moderation desk shows a real field-by-field diff.
                publicRevision = id(K_REVISION, n * 10 + 1);
                insertRevision(publicRevision, listingId, 1, "APPROVED", title, purpose, type, price, area, description, place, address, lat, lng, bedrooms, bathrooms, floors, frontage, road, direction, legal, age, media, n * 10 + 1);
                String editedTitle = title.replace("view thoáng", "full nội thất").replace("ô tô đỗ cửa", "mới sửa đẹp");
                if (editedTitle.equals(title)) editedTitle = title + " (giá tốt)";
                insertRevision(id(K_REVISION, n * 10 + 2), listingId, 2, "SUBMITTED", editedTitle, purpose, type, roundTo(price * 94 / 100, 10_000_000L), area, description + "\nCập nhật: chủ nhà điều chỉnh giá và bổ sung nội thất.", place, address, lat, lng, bedrooms, bathrooms, floors, frontage, road, direction, legal, Duration.ofHours(2 + n % 20), pickImages(images, n + 1), n * 10 + 2);
            }
            case "PENDING_REVIEW" -> insertRevision(id(K_REVISION, n * 10 + 1), listingId, 1, "SUBMITTED", title, purpose, type, price, area, description, place, address, lat, lng, bedrooms, bathrooms, floors, frontage, road, direction, legal, Duration.ofHours(3 + n % 30), media, n * 10 + 1);
            case "DRAFT" -> insertRevision(id(K_REVISION, n * 10 + 1), listingId, 1, "DRAFT", title, purpose, type, price, area, description, place, address, lat, lng, bedrooms, bathrooms, floors, frontage, road, direction, legal, age, media.subList(0, Math.min(1, media.size())), n * 10 + 1);
            case "REJECTED" -> insertRevision(id(K_REVISION, n * 10 + 1), listingId, 1, "REJECTED", title, purpose, type, price / 3, area, description, place, address, lat, lng, bedrooms, bathrooms, floors, frontage, road, direction, legal, age, media, n * 10 + 1);
            default -> throw new IllegalStateException("Unknown listing state " + state);
        }
        if (publicRevision != null) jdbc.update("UPDATE listings SET public_revision_id=? WHERE id=?", publicRevision, listingId);
        // Structured attributes (contract §2.1), the same on every revision of the listing.
        String furnishing = switch (type) {
            case "APARTMENT" -> APARTMENT_FURNISHING[n % APARTMENT_FURNISHING.length];
            case "HOUSE", "TOWNHOUSE" -> n % 2 == 0 ? "BASIC" : "NONE";
            case "VILLA" -> "FULL";
            default -> null;
        };
        boolean rent = purpose.equals("RENT");
        jdbc.update("""
                UPDATE listing_revisions SET legal_status_code=?, furnishing=?, monthly_service_fee_vnd=?, deposit_vnd=?, project_id=?
                WHERE listing_id=?""",
                legalCode(legal), furnishing, rent && type.equals("APARTMENT") ? roundTo(area * 12_000, 10_000L) : null,
                rent ? price * (1 + n % 2) : null, type.equals("APARTMENT") ? projectsByDistrict.get(place.districtCode()) : null, listingId);
        return new SeededListing(listingId, owner, listingStatus, title, purpose, price);
    }

    private void insertRevision(UUID revisionId, UUID listingId, int number, String status, String title, String purpose, String type,
                                long price, double area, String description, Place place, String address, double lat, double lng,
                                Integer bedrooms, Integer bathrooms, Integer floors, Double frontage, Double road, String direction,
                                String legal, Duration age, List<String> media, int mediaSeed) {
        Timestamp created = ago(age);
        boolean submitted = !status.equals("DRAFT");
        boolean moderated = status.equals("APPROVED") || status.equals("REJECTED");
        String note = status.equals("APPROVED") ? "Nội dung đã được kiểm duyệt."
                : status.equals("REJECTED") ? "Giá đăng thấp bất thường so với khu vực (PRICE_UNREALISTIC). Vui lòng cập nhật giá thực tế." : null;
        jdbc.update("""
                INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,description,
                    province_code,district_code,ward_code,address_summary,public_latitude,public_longitude,created_at,submitted_at,moderated_at,
                    moderation_note,bedrooms,bathrooms,floors,frontage_m,road_width_m,direction,legal_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,'01',?,NULL,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                revisionId, listingId, number, status, title, purpose, type, price, area, description, place.districtCode(), address,
                round4(lat), round4(lng), created, submitted ? created : null, moderated ? ago(age.minus(Duration.ofHours(1)).isNegative() ? Duration.ZERO : age.minus(Duration.ofHours(1))) : null,
                note, bedrooms, bathrooms, floors, frontage, road, direction, legal);
        for (int i = 0; i < media.size(); i++) {
            jdbc.update("INSERT INTO listing_media(id,revision_id,media_url,is_primary,sort_order,created_at) VALUES (?,?,?,?,?,?)",
                    id(K_MEDIA, mediaSeed * 10L + i), revisionId, media.get(i), i == 0, i, created);
        }
    }

    private int seedLeads(List<SeededListing> listings, List<Person> realSellers, List<Person> realBuyers, List<Person> fakeBuyers) {
        String[] notes = {
                "Tôi muốn hẹn xem nhà vào cuối tuần này, buổi sáng.",
                "Cho tôi hỏi giá còn thương lượng được không?",
                "Nhà còn không ạ? Tôi cần chuyển vào ở trong tháng tới.",
                "Pháp lý đã có sổ chưa? Có hỗ trợ vay ngân hàng không?",
                "Tôi muốn xem thêm ảnh thực tế và hỏi về chi phí quản lý.",
                "Gia đình tôi 4 người, cần tư vấn thêm về trường học gần đó.",
                "Tôi làm việc gần đây, muốn thuê dài hạn 2 năm.",
                "Có chỗ để ô tô không? Đường vào có rộng không?",
        };
        String[] statuses = {"NEW", "NEW", "CONTACTED", "APPOINTED", "CLOSED", "CONTACTED", "NEW", "SPAM", "APPOINTED", "CLOSED"};
        List<SeededListing> active = listings.stream().filter(l -> l.status().equals("ACTIVE")).toList();
        int n = 0;
        for (SeededListing listing : active) {
            boolean realOwner = realSellers.stream().anyMatch(p -> p.id().equals(listing.owner().id()));
            int count = realOwner ? 2 + (int) (Math.abs(listing.id().getLeastSignificantBits()) % 4) : (int) (Math.abs(listing.id().getLeastSignificantBits()) % 3);
            for (int i = 0; i < count; i++) {
                n++;
                Person requester = fakeBuyers.get(n % fakeBuyers.size());
                insertLead(n, listing, requester, notes[n % notes.length], statuses[n % statuses.length], n % 3 == 0 ? "CONSULTATION" : "VIEWING",
                        n % 7 == 0 ? Duration.ofMinutes(20 + n % 40) : Duration.ofHours(3 + (n * 5L) % 400));
            }
        }
        // Real USER accounts get a sent-inquiry history on other people's listings ("Yêu cầu đã gửi").
        List<SeededListing> others = active.stream().filter(l -> !l.owner().real()).toList();
        for (Person buyer : realBuyers) {
            for (int i = 0; i < Math.min(7, others.size()); i++) {
                n++;
                SeededListing listing = others.get((i * 3 + buyer.name().length()) % others.size());
                insertLead(n, listing, buyer, notes[(n + i) % notes.length], statuses[(i * 3) % statuses.length], i % 2 == 0 ? "VIEWING" : "CONSULTATION",
                        Duration.ofHours(5 + i * 29L));
            }
        }
        return n;
    }

    private void insertLead(int n, SeededListing listing, Person requester, String note, String status, String type, Duration age) {
        PiiProtectionService.ProtectedValue phone = pii.protect(fakePhone(100 + n));
        // Leads past NEW were answered by the owner side 10-130 minutes after they arrived (never later than the clock).
        Duration responseDelay = Duration.ofMinutes(10 + (n * 17L) % 120);
        Duration responseAge = age.minus(responseDelay).isNegative() ? Duration.ZERO : age.minus(responseDelay);
        Timestamp created = ago(age);
        Timestamp firstResponse = status.equals("NEW") ? null : ago(responseAge);
        jdbc.update("""
                INSERT INTO leads(id,listing_id,full_name,phone_encrypted,phone_lookup_hash,note,consent_policy,status,created_at,request_type,
                                  requester_id,updated_at,first_response_at)
                VALUES (?,?,?,?,?,?,TRUE,?,?,?,?,?,?)""",
                id(K_LEAD, n), listing.id(), requester.name(), phone.encrypted(), phone.blindIndex(), note, status, created, type,
                requester.id(), firstResponse != null ? firstResponse : created, firstResponse);
    }

    private int seedReports(List<SeededListing> listings) {
        List<SeededListing> targets = listings.stream().filter(l -> l.status().equals("ACTIVE") && !l.owner().real()).toList();
        Object[][] reports = {
                {"SCAM_DEPOSIT", "P0_EMERGENCY", "PENDING", "Người đăng yêu cầu chuyển khoản 20 triệu giữ chỗ trước khi cho xem nhà."},
                {"FAKE_SOLD", "HIGH", "PENDING", "Tôi gọi thì được báo căn này đã bán từ tháng trước nhưng tin vẫn hiển thị."},
                {"INCORRECT_PRICE", "MEDIUM", "PENDING", "Giá trên tin thấp hơn nhiều so với giá báo khi liên hệ trực tiếp."},
                {"OTHER", "LOW", "WAITING_REPLY", "Ảnh trong tin có vẻ lấy từ một dự án khác, không đúng hiện trạng."},
                {"FAKE_SOLD", "MEDIUM", "RESOLVED", "Tin đăng trùng lặp với một tin khác của cùng người đăng."},
                {"INCORRECT_PRICE", "LOW", "DISMISSED", "Giá thuê chưa gồm phí dịch vụ nhưng tin không ghi rõ."},
                {"SCAM_DEPOSIT", "HIGH", "APPEALED", "Nghi ngờ môi giới giả danh chủ nhà để thu phí xem nhà."},
                {"OTHER", "MEDIUM", "PENDING", "Địa chỉ trên bản đồ lệch khá xa so với vị trí thực tế."},
        };
        int count = Math.min(reports.length, targets.size());
        for (int i = 0; i < count; i++) {
            SeededListing target = targets.get((i * 4) % targets.size());
            String status = (String) reports[i][2];
            boolean closed = status.equals("RESOLVED") || status.equals("DISMISSED");
            jdbc.update("""
                    INSERT INTO listing_reports(id,listing_id,case_number,reporter_type,reporter_phone,category,severity,status,description,resolution_note,created_at,resolved_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                    id(K_REPORT, i + 1), target.id(), String.format("UAT-CASE-%04d", i + 1), "ANONYMOUS", i % 2 == 0 ? pii.protect(fakePhone(300 + i)).encrypted() : null,
                    reports[i][0], reports[i][1], status, reports[i][3],
                    closed ? (status.equals("RESOLVED") ? "Đã yêu cầu người đăng gỡ tin trùng." : "Nội dung không vi phạm quy định đăng tin.") : null,
                    ago(Duration.ofHours(6 + i * 17L)), closed ? ago(Duration.ofHours(2 + i * 5L)) : null);
        }
        return count;
    }

    private int seedVerifications(List<SeededListing> listings) {
        List<SeededListing> targets = listings.stream().filter(l -> l.status().equals("ACTIVE") || l.status().equals("PENDING_REVIEW")).toList();
        String[][] items = {
                {"CERTIFICATE_OF_OWNERSHIP", "PENDING"}, {"PROJECT_PURCHASE_CONTRACT", "PENDING"}, {"POWER_OF_ATTORNEY", "PENDING"},
                {"CERTIFICATE_OF_OWNERSHIP", "PENDING"}, {"CERTIFICATE_OF_OWNERSHIP", "VERIFIED_OWNER"}, {"PROJECT_PURCHASE_CONTRACT", "VERIFIED_OWNER"},
                {"CERTIFICATE_OF_OWNERSHIP", "REJECTED"}, {"POWER_OF_ATTORNEY", "REVOKED"},
        };
        int count = Math.min(items.length, targets.size());
        for (int i = 0; i < count; i++) {
            SeededListing target = targets.get((i * 5 + 1) % targets.size());
            String status = items[i][1];
            String note = switch (status) {
                case "VERIFIED_OWNER" -> "Thông tin trên sổ khớp với hồ sơ eKYC của người đăng.";
                case "REJECTED" -> "Tên trên giấy chứng nhận không khớp với người đăng tin.";
                case "REVOKED" -> "Giấy ủy quyền đã hết hiệu lực.";
                default -> null;
            };
            jdbc.update("""
                    INSERT INTO listing_verifications(id,listing_id,user_kyc_id,verification_type,certificate_number,document_urls,owner_name_on_doc,status,verifier_note,created_at,verified_at,expires_at,revoked_at)
                    VALUES (?,?,?,?,?,NULL,?,?,?,?,?,?,?)""",
                    id(K_VERIFICATION, i + 1), target.id(), target.owner().kycId(), items[i][0], String.format("UAT-GCN-%06d", 482000 + i * 137),
                    target.owner().name(), status, note, ago(Duration.ofHours(4 + i * 13L)), status.equals("PENDING") ? null : ago(Duration.ofHours(1 + i * 3L)),
                    status.equals("VERIFIED_OWNER") ? Timestamp.from(now.minus(Duration.ofHours(1 + i * 3L)).plus(Duration.ofDays(180))) : null,
                    status.equals("REVOKED") ? ago(Duration.ofHours(1 + i * 3L)) : null);
        }
        return count;
    }

    private int seedProjects() {
        Object[][] projects = {
                {"Khu đô thị Sông Hồng Xanh", "Công ty CP Phát triển Đô thị Minh Long", "004", "Đường Nguyễn Văn Cừ, Long Biên, Hà Nội", 42.5, 12, 3600, 2027, "UNDER_CONSTRUCTION"},
                {"Chung cư An Phú Residence", "Công ty TNHH Đầu tư An Phú Thịnh", "005", "Phố Trần Duy Hưng, Cầu Giấy, Hà Nội", 1.8, 2, 640, 2024, "COMPLETED"},
                {"Tổ hợp Lạc Hồng Tower", "Công ty CP Bất động sản Lạc Hồng", "009", "Đường Nguyễn Trãi, Thanh Xuân, Hà Nội", 3.2, 3, 1150, 2026, "ACTIVE"},
                {"Khu nhà ở Vườn Đào Tây Hồ", "Công ty CP Địa ốc Hồ Tây Việt", "003", "Đường Lạc Long Quân, Tây Hồ, Hà Nội", 6.4, 4, 420, 2028, "PLANNING"},
                {"Khu đô thị Hà Đông Park", "Tập đoàn Đầu tư Thành Nam", "268", "Đường Tố Hữu, Hà Đông, Hà Nội", 28.0, 9, 2900, 2025, "ACTIVE"},
                {"Chung cư Hoàng Mai Garden", "Công ty CP Xây dựng Sao Mai Hà Nội", "008", "Đường Tam Trinh, Hoàng Mai, Hà Nội", 2.1, 2, 780, 2025, "COMPLETED"},
                {"Làng biệt thự Đông Anh Riverside", "Công ty TNHH Phát triển Nhà Bắc Sông", "017", "Đường Võ Nguyên Giáp, Đông Anh, Hà Nội", 55.0, 1, 310, 2029, "PLANNING"},
                {"Tòa nhà Mỹ Đình Sky", "Công ty CP Đầu tư Mỹ Đình Xanh", "019", "Đường Lê Đức Thọ, Nam Từ Liêm, Hà Nội", 1.2, 1, 380, 2023, "LOCKED"},
        };
        for (int i = 0; i < projects.length; i++) {
            Object[] p = projects[i];
            if (!p[8].equals("PLANNING") && !p[8].equals("LOCKED")) projectsByDistrict.putIfAbsent((String) p[2], id(K_PROJECT, i + 1));
            jdbc.update("""
                    INSERT INTO projects(id,name,slug,developer_name,province_code,district_code,address,total_area_m2,total_blocks,total_units,handover_year,legal_license_number,status,created_at,updated_at)
                    VALUES (?,?,?,?,'01',?,?,?,?,?,?,?,?,?,?)""",
                    id(K_PROJECT, i + 1), p[0], "uat-" + slugify((String) p[0]), p[1], p[2], p[3], ((Double) p[4]) * 10_000, p[5], p[6], p[7],
                    String.format("UAT-GP-%d/QĐ-UBND", 1200 + i * 37), p[8], ago(Duration.ofDays(30 + i * 11L)), ago(Duration.ofDays(1 + i)));
        }
        return projects.length;
    }

    private int seedArticles(List<String> images) {
        Object[][] articles = {
                {"KNOWLEDGE", "PUBLISHED", "5 bước kiểm tra pháp lý trước khi đặt cọc mua nhà", "Checklist ngắn giúp người mua tránh rủi ro khi giao dịch nhà đất."},
                {"LEGAL_POLICY", "PUBLISHED", "Những điểm mới của Luật Đất đai 2024 người mua cần biết", "Tóm tắt các thay đổi ảnh hưởng trực tiếp tới giao dịch nhà ở."},
                {"MARKET_INSIGHTS", "PUBLISHED", "Thị trường căn hộ Hà Nội quý III/2026: nguồn cung và mặt bằng giá", "Số liệu tham khảo về nguồn cung mới và biên độ giá theo khu vực."},
                {"KNOWLEDGE", "SUBMITTED", "Kinh nghiệm thuê căn hộ lần đầu cho người đi làm", "Các khoản phí, điều khoản hợp đồng và lưu ý khi nhận bàn giao."},
                {"MARKET_INSIGHTS", "SUBMITTED", "Giá đất ven đô phía Đông Hà Nội sau khi mở cầu mới", "Phân tích xu hướng giá đất khu vực Long Biên, Gia Lâm, Đông Anh."},
                {"LEGAL_POLICY", "DRAFT", "Thuế và phí khi chuyển nhượng nhà đất năm 2026", "Bảng tính nhanh các khoản thuế phí bên mua và bên bán cần nộp."},
                {"KNOWLEDGE", "REJECTED", "Có nên mua nhà trong ngõ nhỏ?", "Ưu nhược điểm của nhà trong ngõ so với nhà mặt phố."},
                {"MARKET_INSIGHTS", "ARCHIVED", "Tổng kết thị trường bất động sản năm 2025", "Nhìn lại diễn biến giá và thanh khoản của năm 2025."},
        };
        for (int i = 0; i < articles.length; i++) {
            Object[] a = articles[i];
            String status = (String) a[1];
            UUID articleId = id(K_ARTICLE, i + 1);
            String title = (String) a[2];
            Timestamp created = ago(Duration.ofDays(4 + i * 6L));
            // Two published articles also carry a newer submitted revision to exercise the review flow.
            boolean hasPendingEdit = status.equals("PUBLISHED") && i < 2;
            UUID publishedRevision = status.equals("PUBLISHED") || status.equals("ARCHIVED") ? id(K_ARTICLE_REV, i * 10 + 1) : null;
            // published_at/first_published_at (V090): an ARCHIVED article was public before, so its URL answers 410
            jdbc.update("""
                    INSERT INTO cms_articles(id,slug,category,status,published_revision_id,created_at,updated_at,published_at,first_published_at,unpublished_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?)""",
                    articleId, "uat-" + slugify(title), a[0], status, publishedRevision, created, ago(Duration.ofDays(1 + i)),
                    publishedRevision == null ? null : created, publishedRevision == null ? null : created,
                    status.equals("ARCHIVED") ? ago(Duration.ofDays(1 + i)) : null);
            String firstStatus = switch (status) { case "PUBLISHED", "ARCHIVED" -> "PUBLISHED"; default -> status; };
            insertArticleRevision(articleId, i * 10 + 1, 1, title, (String) a[3], firstStatus, created, images, i);
            if (hasPendingEdit) {
                insertArticleRevision(articleId, i * 10 + 2, 2, title + " (cập nhật)", a[3] + " Bổ sung ví dụ thực tế.", "SUBMITTED", ago(Duration.ofHours(5 + i)), images, i);
            }
        }
        return articles.length;
    }

    private void insertArticleRevision(UUID articleId, long seq, int number, String title, String summary, String status, Timestamp created, List<String> images, int i) {
        String html = "<p>" + summary + "</p>"
                + "<h2>Những điểm chính</h2><ul><li>Kiểm tra thông tin người bán và giấy tờ gốc.</li><li>So sánh giá với các tin cùng khu vực.</li><li>Chỉ đặt cọc khi hợp đồng ghi rõ điều kiện hoàn cọc.</li></ul>"
                + "<p>Nội dung mang tính tham khảo, không thay thế tư vấn pháp lý chuyên nghiệp.</p>"
                + "<p><em>Bài viết mẫu phục vụ kiểm thử giao diện.</em></p>";
        boolean reviewed = status.equals("PUBLISHED") || status.equals("REJECTED");
        jdbc.update("""
                INSERT INTO cms_article_revisions(id,article_id,revision_number,title,summary,content_html,cover_image_url,author_name,legal_reference,
                    meta_description,canonical_url,status,rejection_reason,created_at,reviewed_at,reviewed_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,NULL,?,?,?,?,?)""",
                id(K_ARTICLE_REV, seq), articleId, number, title, summary, html, images.isEmpty() ? null : images.get(i % images.size()),
                i % 2 == 0 ? "Ban biên tập Nhà Đất Chuẩn" : "Chuyên viên pháp lý Nguyễn An", i % 3 == 1 ? "Luật Đất đai số 31/2024/QH15" : null,
                summary, status, status.equals("REJECTED") ? "Cần bổ sung nguồn số liệu và dẫn chứng pháp lý." : null,
                created, reviewed ? created : null, reviewed ? "moderator.test@nhadatchuan.online" : null);
    }

    private int seedOrders(List<Person> real, List<Person> fakeBrokers) {
        Map<String, Object> bank = jdbc.queryForList("SELECT bank_bin,account_number,account_name FROM bank_settings WHERE singleton_id=1").stream().findFirst().orElse(Map.of());
        UUID reviewer = real.stream().filter(p -> p.role().equals("ADMIN")).map(Person::id).findFirst().orElse(null);
        int n = 0;
        for (Person person : real) {
            if (!person.role().equals("BROKER") && !person.role().equals("ADMIN")) continue;
            // Past history only: the approved order has already expired, so the account's current plan stays as it is.
            insertOrder(++n, person, "STANDARD", "APPROVED", Duration.ofDays(75), reviewer, bank);
            insertOrder(++n, person, "PRO", "APPROVED", Duration.ofDays(40), reviewer, bank);
            insertOrder(++n, person, "PRO", "REJECTED", Duration.ofDays(20), reviewer, bank);
            insertOrder(++n, person, "STANDARD", "CANCELLED", Duration.ofDays(9), null, bank);
        }
        String[] fakeStatuses = {"TRANSFER_REPORTED", "TRANSFER_REPORTED", "APPROVED", "TRANSFER_REPORTED", "REJECTED", "APPROVED", "TRANSFER_REPORTED", "APPROVED"};
        for (int i = 0; i < fakeStatuses.length; i++) {
            Person person = fakeBrokers.get(i % fakeBrokers.size());
            String status = fakeStatuses[i];
            insertOrder(++n, person, i % 2 == 0 ? "PRO" : "STANDARD", status, status.equals("TRANSFER_REPORTED") ? Duration.ofHours(2 + i * 7L) : Duration.ofDays(5 + i * 4L),
                    status.equals("TRANSFER_REPORTED") ? null : reviewer, bank);
        }
        return n;
    }

    private void insertOrder(int n, Person person, String plan, String status, Duration age, UUID reviewer, Map<String, Object> bank) {
        Map<String, Object> planRow = jdbc.queryForMap("SELECT name,price_vnd,listing_quota,duration_days FROM service_plans WHERE code=?", plan);
        UUID orderId = id(K_ORDER, n);
        boolean reported = !status.equals("CREATED") && !status.equals("CANCELLED");
        boolean reviewed = status.equals("APPROVED") || status.equals("REJECTED");
        Timestamp created = ago(age);
        Timestamp reportedAt = reported ? ago(age.minus(Duration.ofMinutes(25))) : null;
        Timestamp reviewedAt = reviewed ? ago(age.minus(Duration.ofHours(3))) : null;
        jdbc.update("""
                INSERT INTO package_orders(id,user_id,plan_code,amount_vnd,transfer_reference,status,user_reported_at,reviewed_by,reviewed_at,review_note,created_at,
                    plan_name_snapshot,quota_snapshot,duration_days_snapshot,bank_bin_snapshot,account_number_snapshot,account_name_snapshot)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                orderId, person.id(), plan, planRow.get("price_vnd"), String.format("UAT%010d", 7_300_000 + n * 17), status, reportedAt,
                reviewed ? reviewer : null, reviewedAt,
                status.equals("REJECTED") ? "Không tìm thấy giao dịch khớp nội dung chuyển khoản." : status.equals("APPROVED") ? "Đã đối soát sao kê." : null,
                created, planRow.get("name"), planRow.get("listing_quota"), planRow.get("duration_days"),
                bank.get("bank_bin"), bank.get("account_number"), bank.get("account_name"));
        if (status.equals("APPROVED")) {
            jdbc.update("INSERT INTO invoices(id,invoice_number,order_id,user_id,amount_vnd,issued_at) VALUES (?,?,?,?,?,?)",
                    id(K_INVOICE, n), String.format("UAT-INV-2026-%05d", n), orderId, person.id(), planRow.get("price_vnd"), reviewedAt);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String describe(String type, String purpose, String street, String ward, String district, Integer bedrooms, Integer floors, Double road) {
        String action = purpose.equals("SALE") ? "Cần bán" : "Cho thuê";
        return switch (type) {
            case "APARTMENT" -> action + " căn hộ " + bedrooms + " phòng ngủ tại khu vực " + ward + ", " + district + ". Căn góc thoáng, nhiều ánh sáng tự nhiên, "
                    + "bếp và phòng khách liên thông. Tòa nhà có bảo vệ 24/7, hầm gửi xe, siêu thị và trường học trong bán kính 1 km. Di chuyển thuận tiện ra " + street + ".";
            case "HOUSE" -> action + " nhà riêng " + floors + " tầng trong ngõ " + street + ", ngõ rộng " + trim(road) + " m, ô tô đỗ cửa. Nhà xây kiên cố, mỗi tầng một phòng ngủ khép kín, "
                    + "sân phơi thoáng. Khu dân trí cao, gần chợ, trường học và bệnh viện.";
            case "TOWNHOUSE" -> action + " nhà phố mặt đường " + street + ", vỉa hè rộng, phù hợp kinh doanh, làm văn phòng hoặc showroom. " + floors + " tầng, thang máy, "
                    + "mặt bằng vuông vắn. Khu vực đông dân cư, lưu lượng người qua lại lớn.";
            case "VILLA" -> action + " biệt thự song lập tại " + ward + ", " + district + ", sân vườn rộng, gara ô tô, " + bedrooms + " phòng ngủ. "
                    + "Khu biệt thự an ninh, nhiều cây xanh, gần hồ điều hòa và khu vui chơi trẻ em.";
            default -> "Cần bán lô đất thổ cư tại " + street + ", " + district + ", đường ô tô " + trim(road) + " m, mặt tiền đẹp, nở hậu. "
                    + "Phù hợp xây nhà ở hoặc đầu tư dài hạn. Khu dân cư hiện hữu, điện nước đầy đủ.";
        };
    }

    /** Same mapping as the V027 backfill for the seeder's fixed legal texts. */
    private static String legalCode(String legal) {
        if (legal == null) return null;
        String text = Normalizer.normalize(legal.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        if (text.matches(".*\\bcho( ra| cap)? so\\b.*")) return "PENDING_CERTIFICATE";
        if (text.contains("so do")) return "RED_BOOK";
        if (text.contains("so hong")) return "PINK_BOOK";
        if (text.contains("hop dong mua ban") || text.contains("hdmb")) return "SALE_CONTRACT";
        return "OTHER";
    }

    private static List<String> pickImages(List<String> pool, int n) {
        if (pool.isEmpty()) return List.of();
        int count = Math.min(pool.size(), 3 + n % 3);
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < count; i++) picked.add(pool.get((n + i * 2) % pool.size()));
        return picked.stream().distinct().toList();
    }

    private static UUID id(int kind, long n) {
        return UUID.fromString(String.format("%s%02x-0000-4000-8000-%012x", ID_PREFIX, kind, n));
    }

    private Timestamp ago(Duration duration) {
        return Timestamp.from(now.minus(duration));
    }

    /** Clearly synthetic numbers in the 0999 range, unique per seed index. */
    private static String fakePhone(int n) {
        return String.format("0999%06d", 100_000 + n);
    }

    private static long roundTo(double value, long step) {
        return Math.max(step, Math.round(value / step) * step);
    }

    private static double round4(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }

    private static String trim(Double value) {
        if (value == null) return "";
        return value == Math.floor(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }

    private static String trim(double value) {
        return trim(Double.valueOf(value));
    }

    private static String slugify(String text) {
        String ascii = Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.length() > 150 ? slug.substring(0, 150).replaceAll("-$", "") : slug;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
