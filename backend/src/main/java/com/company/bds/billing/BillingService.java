package com.company.bds.billing;

import com.company.bds.iam.application.AuthService;
import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.mail.MailAddressValidator;
import com.company.bds.shared.mail.MailMessage;
import com.company.bds.shared.mail.MailOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service packages (paid listing quota) — a payment for the platform's posting service, never a property deposit.
 *
 * <p>F18.2 idempotency: an {@code Idempotency-Key} is scoped to the actor (same key + same plan → the same order, same
 * key + another plan → 409), and order creation is serialised per user+plan with an advisory lock so an open order
 * (CREATED / TRANSFER_REPORTED / EXCEPTION) is returned instead of creating another. F18.3: bank settings are saved
 * with compare-and-set on {@code version} (409 on a stale version). F18.4: every transition is a single guarded
 * UPDATE (the quota is granted only by the one request whose UPDATE changed the row) and is appended to
 * {@code package_order_events}; mails go through the outbox.</p>
 */
@Service
public class BillingService {
    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    static final Set<String> OPEN = Set.of("CREATED", "TRANSFER_REPORTED", "EXCEPTION");
    private static final Set<String> STATUSES = Set.of("CREATED", "TRANSFER_REPORTED", "EXCEPTION", "APPROVED", "REJECTED", "REFUNDED", "CANCELLED");
    private static final String ORDER_COLUMNS = """
            o.id,o.user_id,o.plan_code,o.amount_vnd,o.transfer_reference,o.status,o.created_at,o.bank_bin_snapshot,
            o.account_number_snapshot,o.account_name_snapshot,o.plan_name_snapshot,o.quota_snapshot,o.duration_days_snapshot,
            o.user_reported_at,o.reviewed_at,o.review_note,o.exception_reason,o.resolution,o.received_amount_vnd,
            o.received_reference,o.updated_at,(SELECT i.invoice_number FROM invoices i WHERE i.order_id=o.id) AS invoice_number
            """;

    private final JdbcTemplate jdbc;
    private final RealtimeNotificationService notifications;
    private final MailOutbox mailOutbox;
    private final MailAddressValidator mailAddresses;
    private final Clock clock;
    private final ObjectMapper json;

    public BillingService(JdbcTemplate jdbc, RealtimeNotificationService notifications, MailOutbox mailOutbox,
                          MailAddressValidator mailAddresses, Clock clock, ObjectMapper json) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.mailOutbox = mailOutbox;
        this.mailAddresses = mailAddresses;
        this.clock = clock;
        this.json = json;
    }

    public List<Plan> plans() {
        return jdbc.query("SELECT code,name,price_vnd,listing_quota,duration_days,description FROM service_plans WHERE active ORDER BY sort_order, code",
                (r, n) -> new Plan(r.getString(1), r.getString(2), r.getLong(3), r.getInt(4), r.getInt(5), r.getString(6)));
    }

    public BankSettings bank() {
        return jdbc.query("SELECT bank_bin,bank_name,account_number,account_name,admin_notification_email,version FROM bank_settings WHERE singleton_id=1",
                (r, n) -> new BankSettings(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getLong(6)))
                .stream().findFirst().orElse(null);
    }

    /** Compare-and-set: {@code expectedVersion} must be the version the admin loaded (null only for the very first save). */
    @Transactional
    public BankSettings saveBank(BankSettings b, Long expectedVersion) {
        if (b.bankBin() == null || !b.bankBin().matches("\\d{6}") || b.accountNumber() == null || !b.accountNumber().matches("\\d{6,19}")) {
            throw new IllegalArgumentException("BIN hoặc số tài khoản không hợp lệ.");
        }
        if (b.bankName() == null || b.bankName().isBlank() || b.accountName() == null || b.accountName().isBlank()) {
            throw new IllegalArgumentException("Cần nhập tên ngân hàng và tên chủ tài khoản.");
        }
        // Same rule as the mail outbox, so a saved address can always be used for the reconciliation notice.
        String adminEmail = b.adminEmail() == null || b.adminEmail().isBlank() ? null : b.adminEmail().trim();
        if (adminEmail != null && !mailAddresses.isValid(adminEmail)) {
            throw new IllegalArgumentException("Email nhận thông báo đối soát không hợp lệ (chỉ một địa chỉ, dạng ten@mien.vn).");
        }
        BankSettings current = bank();
        int changed;
        if (current == null) {
            changed = jdbc.update("""
                    INSERT INTO bank_settings(singleton_id,bank_bin,bank_name,account_number,account_name,admin_notification_email,version)
                    VALUES(1,?,?,?,?,?,0) ON CONFLICT (singleton_id) DO NOTHING
                    """, b.bankBin(), b.bankName().trim(), b.accountNumber(), b.accountName().trim(), adminEmail);
        } else {
            if (expectedVersion == null) {
                throw ApiException.badRequest("EXPECTED_VERSION_REQUIRED", "Thiếu phiên bản cấu hình đang sửa; hãy tải lại trang.");
            }
            changed = jdbc.update("""
                    UPDATE bank_settings SET bank_bin=?,bank_name=?,account_number=?,account_name=?,admin_notification_email=?,
                        updated_at=?,version=version+1
                    WHERE singleton_id=1 AND version=?
                    """, b.bankBin(), b.bankName().trim(), b.accountNumber(), b.accountName().trim(), adminEmail,
                    Timestamp.from(clock.instant()), expectedVersion);
        }
        if (changed == 0) {
            throw ApiException.conflict("BANK_SETTINGS_CONFLICT", "Cấu hình tài khoản nhận tiền vừa được quản trị viên khác thay đổi. Hãy tải lại và kiểm tra trước khi lưu.");
        }
        return bank();
    }

    // ------------------------------------------------------------------------------------------------ user side

    @Transactional
    public CreateResult create(UUID userId, String planCode, String idempotencyKey) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && (key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+"))) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key tối đa 128 ký tự chữ, số, . _ : -");
        }
        Plan plan = plans().stream().filter(x -> x.code().equals(planCode) && x.priceVnd() > 0).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Gói dịch vụ không hợp lệ."));
        // Serialise per user+key first (one key is never bound to two orders, whatever plan each request names), then per
        // user+plan (concurrent requests with or without a key see each other's order). Always in this order: no deadlock.
        if (key != null) {
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))", Object.class, "billing-key:" + userId, key);
        }
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))", Object.class, "billing-order:" + userId, plan.code());
        String scope = "billing-order:" + userId;
        String requestHash = AuthService.sha256("plan:" + plan.code());
        if (key != null) {
            List<Object[]> previous = jdbc.query("SELECT request_hash, resource_id FROM api_idempotency_keys WHERE scope=? AND idempotency_key=? "
                            + "AND " + KEY_ALIVE,
                    (rs, n) -> new Object[]{rs.getString(1), rs.getObject(2, UUID.class)}, scope, key);
            if (!previous.isEmpty()) {
                if (!requestHash.equals(previous.get(0)[0])) {
                    throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key này đã dùng cho một gói khác.");
                }
                return new CreateResult(load((UUID) previous.get(0)[1], userId, false), false);
            }
        }
        List<UUID> open = jdbc.queryForList("""
                SELECT id FROM package_orders WHERE user_id=? AND plan_code=? AND status IN ('CREATED','TRANSFER_REPORTED','EXCEPTION')
                ORDER BY created_at DESC, id DESC LIMIT 1
                """, UUID.class, userId, plan.code());
        UUID id;
        boolean created;
        if (!open.isEmpty()) {
            id = open.get(0);
            created = false;
        } else {
            BankSettings b = bank();
            if (b == null) throw new IllegalStateException("Admin chưa cấu hình tài khoản nhận tiền.");
            id = UUID.randomUUID();
            String ref = ("BDS" + id.toString().replace("-", "").substring(0, 12)).toUpperCase(Locale.ROOT);
            Instant now = clock.instant();
            jdbc.update("""
                    INSERT INTO package_orders(id,user_id,plan_code,amount_vnd,transfer_reference,status,plan_name_snapshot,quota_snapshot,
                        duration_days_snapshot,bank_bin_snapshot,account_number_snapshot,account_name_snapshot,created_at,updated_at)
                    VALUES(?,?,?,?,?,'CREATED',?,?,?,?,?,?,?,?)
                    """, id, userId, plan.code(), plan.priceVnd(), ref, plan.name(), plan.quota(), plan.durationDays(), b.bankBin(),
                    b.accountNumber(), b.accountName(), Timestamp.from(now), Timestamp.from(now));
            event(id, "CREATED", null, "CREATED", userId, null, Map.of("planCode", plan.code(), "amountVnd", plan.priceVnd()));
            created = true;
        }
        if (key != null) {
            int bound = jdbc.update("INSERT INTO api_idempotency_keys(scope,idempotency_key,request_hash,resource_id,expires_at) "
                            + "VALUES(?,?,?,?, now() + interval '24 hours') " + REUSE_EXPIRED_KEY, scope, key, requestHash, id);
            if (bound == 0) {
                // Defence in depth behind the key lock: never leave an order the key does not point at.
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key này vừa được dùng cho một yêu cầu khác.");
            }
        }
        return new CreateResult(load(id, userId, false), created);
    }

    @Transactional
    public Order report(UUID id, UUID userId) {
        int changed = jdbc.update("""
                UPDATE package_orders SET status='TRANSFER_REPORTED',user_reported_at=?,updated_at=?,version=version+1
                WHERE id=? AND user_id=? AND status='CREATED'
                """, now(), now(), id, userId);
        Order order = load(id, userId, false);
        // A retry of a report that already happened is answered with the order; any other state means another action won.
        if (changed == 0 && !"TRANSFER_REPORTED".equals(order.status())) throw stateChanged(order.status());
        if (changed > 0) {
            event(id, "TRANSFER_REPORTED", "CREATED", "TRANSFER_REPORTED", userId, null, null);
            notifications.notify(userId, "PAYMENT_REPORTED", "Đã gửi đối soát", "Yêu cầu " + order.reference() + " đã chuyển đến quản trị viên.");
            emailAdmin(order);
        }
        return order;
    }

    @Transactional
    public Order cancel(UUID id, UUID userId) {
        int changed = jdbc.update("""
                UPDATE package_orders SET status='CANCELLED',reviewed_at=?,review_note='Người dùng đã hủy',updated_at=?,version=version+1
                WHERE id=? AND user_id=? AND status='CREATED'
                """, now(), now(), id, userId);
        if (changed > 0) event(id, "CANCELLED", "CREATED", "CANCELLED", userId, "Người dùng đã hủy", null);
        Order order = load(id, userId, false);
        // Cancel racing an admin receipt: if the admin won, the order was paid/approved, not cancelled — say so (409)
        // instead of a 200 the client would read as "cancelled". A retry of a done cancel is answered with the order.
        if (changed == 0 && !"CANCELLED".equals(order.status())) throw stateChanged(order.status());
        return order;
    }

    @Transactional(readOnly = true)
    public OrderPage mine(UUID userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        Long total = jdbc.queryForObject("SELECT count(*) FROM package_orders WHERE user_id=?", Long.class, userId);
        List<Order> items = jdbc.query("SELECT " + ORDER_COLUMNS + " FROM package_orders o WHERE o.user_id=? ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?",
                (r, n) -> map(r), userId, safeSize, safePage * safeSize);
        return new OrderPage(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public OrderDetail detail(UUID id, UUID userId, boolean admin) {
        Order order = load(id, userId, admin);
        return new OrderDetail(order, events(id, admin));
    }

    // ------------------------------------------------------------------------------------------------ admin side

    /** Reconciliation queue by status (default TRANSFER_REPORTED), oldest report first, stable order, bounded page. */
    @Transactional(readOnly = true)
    public AdminOrderPage reconciliation(String status, int page, int size) {
        String normalized = status == null || status.isBlank() ? "TRANSFER_REPORTED" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw ApiException.badRequest("INVALID_FILTER", "Trạng thái đơn không hợp lệ.");
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Long total = jdbc.queryForObject("SELECT count(*) FROM package_orders WHERE status=?", Long.class, normalized);
        List<AdminOrder> items = jdbc.query(ADMIN_SELECT + " WHERE o.status=? ORDER BY o.user_reported_at ASC NULLS LAST, o.created_at ASC, o.id ASC LIMIT ? OFFSET ?",
                (r, n) -> mapAdmin(r), normalized, safeSize, safePage * safeSize);
        return new AdminOrderPage(items, safePage, safeSize, total == null ? 0 : total, counts());
    }

    @Transactional(readOnly = true)
    public AdminOrderPage adminOrders(int page, int size, String status, String keyword) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String predicates = " WHERE (?='' OR o.status=?) AND (?='' OR o.transfer_reference ILIKE ? OR u.full_name ILIKE ? OR u.email ILIKE ?)";
        String like = "%" + normalizedKeyword + "%";
        Long total = jdbc.queryForObject("SELECT count(*) FROM package_orders o JOIN users u ON u.id=o.user_id" + predicates, Long.class,
                normalizedStatus, normalizedStatus, normalizedKeyword, like, like, like);
        List<AdminOrder> items = jdbc.query(ADMIN_SELECT + predicates + " ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?",
                (r, n) -> mapAdmin(r), normalizedStatus, normalizedStatus, normalizedKeyword, like, like, like, safeSize, safePage * safeSize);
        return new AdminOrderPage(items, safePage, safeSize, total == null ? 0 : total, counts());
    }

    /**
     * Records what actually arrived on the account. Exact amount and the transfer reference present → approved
     * (quota granted once); anything else → EXCEPTION for an explicit resolution.
     */
    @Transactional
    public Order recordReceipt(UUID id, UUID adminId, long receivedAmount, String receivedReference, String note) {
        return recordReceipt(id, adminId, receivedAmount, receivedReference, note, null).order();
    }

    @Transactional
    public Review recordReceipt(UUID id, UUID adminId, long receivedAmount, String receivedReference, String note, String idempotencyKey) {
        if (receivedAmount < 0) throw ApiException.badRequest("INVALID_AMOUNT", "Số tiền nhận không hợp lệ.");
        String reference = receivedReference == null ? "" : receivedReference.trim();
        if (reference.length() > 100) throw ApiException.badRequest("INVALID_REFERENCE", "Nội dung chuyển khoản tối đa 100 ký tự.");
        ReviewKey key = reviewKey(adminId, idempotencyKey, "receipt", id, writeJson(new ReceiptPayload(receivedAmount, reference, trimOrNull(note))));
        Review locked = lockForReview(id, Set.of("CREATED", "TRANSFER_REPORTED"), key);
        if (locked.replayed()) return locked;
        Order order = locked.order();
        boolean amountOk = receivedAmount == order.amountVnd();
        boolean referenceOk = normalize(reference).contains(normalize(order.reference()));
        jdbc.update("UPDATE package_orders SET received_amount_vnd=?, received_reference=? WHERE id=?", receivedAmount, reference.isEmpty() ? null : reference, id);
        if (amountOk && referenceOk) {
            return bind(key, applyApproval(order, adminId, note == null || note.isBlank() ? "Khớp số tiền và nội dung chuyển khoản" : note.trim(), "MATCHED",
                    Set.of("CREATED", "TRANSFER_REPORTED")));
        }
        String reason = !amountOk && !referenceOk ? "AMOUNT_AND_REFERENCE_MISMATCH" : !amountOk ? "AMOUNT_MISMATCH" : "REFERENCE_MISMATCH";
        int changed = jdbc.update("""
                UPDATE package_orders SET status='EXCEPTION', exception_reason=?, exception_at=?, reviewed_by=?, review_note=?, updated_at=?, version=version+1
                WHERE id=? AND status IN ('CREATED','TRANSFER_REPORTED')
                """, reason, now(), adminId, trimOrNull(note), now(), id);
        if (changed == 0) throw ApiException.conflict("ORDER_STATE_CHANGED", "Đơn vừa được xử lý bởi thao tác khác.");
        event(id, "EXCEPTION", order.status(), "EXCEPTION", adminId, trimOrNull(note),
                Map.of("reason", reason, "receivedAmountVnd", receivedAmount, "receivedReference", reference, "expectedAmountVnd", order.amountVnd()));
        String message = "Khoản chuyển cho yêu cầu " + order.reference() + " chưa khớp (" + label(reason)
                + "). Quản trị viên sẽ liên hệ để xử lý; gói chưa được kích hoạt.";
        notifications.notify(order.userId(), "PAYMENT_EXCEPTION", "Thanh toán cần đối chiếu thêm", message);
        emailUser(order, "[Nhà Đất Chuẩn] Thanh toán cần đối chiếu thêm " + order.reference(), message, "EXCEPTION");
        return bind(key, load(id, null, true));
    }

    /** Resolution of an EXCEPTION: APPROVE_WITH_NOTE, REJECT or REFUNDED_OFFLINE, always with a note. */
    @Transactional
    public Order resolveException(UUID id, UUID adminId, String resolution, String note) {
        return resolveException(id, adminId, resolution, note, null).order();
    }

    @Transactional
    public Review resolveException(UUID id, UUID adminId, String resolution, String note, String idempotencyKey) {
        String why = note == null ? "" : note.trim();
        if (why.length() < 5) throw ApiException.badRequest("NOTE_REQUIRED", "Cần ghi chú cách xử lý (ít nhất 5 ký tự).");
        String normalized = resolution == null ? "" : resolution.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE_WITH_NOTE", "REJECT", "REFUNDED_OFFLINE").contains(normalized)) {
            throw ApiException.badRequest("INVALID_RESOLUTION", "Cách xử lý không hợp lệ.");
        }
        ReviewKey key = reviewKey(adminId, idempotencyKey, "resolve", id, normalized + "|" + why);
        Review locked = lockForReview(id, Set.of("EXCEPTION"), key);
        if (locked.replayed()) return locked;
        Order order = locked.order();
        return bind(key, switch (normalized) {
            case "APPROVE_WITH_NOTE" -> applyApproval(order, adminId, why, "APPROVED_WITH_NOTE", Set.of("EXCEPTION"));
            case "REJECT" -> applyTerminal(order, adminId, why, "REJECTED", "REJECTED", "EXCEPTION");
            default -> applyTerminal(order, adminId, why, "REFUNDED", "REFUNDED_OFFLINE", "EXCEPTION");
        });
    }

    /**
     * Deprecated v1 approval without receipt details. It bypasses amount/reference matching, so it is a manual override
     * like {@code APPROVE_WITH_NOTE}: ADMIN only (route), a written note of at least 5 characters, only from
     * TRANSFER_REPORTED under a row lock, 409 {@code ORDER_STATE_CHANGED} otherwise. New clients use {@code /receipt}.
     */
    @Deprecated
    @Transactional
    public Order approve(UUID id, UUID adminId, String note) {
        return approve(id, adminId, note, null).order();
    }

    @Deprecated
    @Transactional
    public Review approve(UUID id, UUID adminId, String note, String idempotencyKey) {
        String why = note == null ? "" : note.trim();
        if (why.length() < 5) {
            throw ApiException.badRequest("NOTE_REQUIRED", "Duyệt không qua đối soát cần ghi chú lý do (ít nhất 5 ký tự); hãy dùng ghi nhận khoản nhận.");
        }
        ReviewKey key = reviewKey(adminId, idempotencyKey, "approve", id, why);
        Review locked = lockForReview(id, Set.of("TRANSFER_REPORTED"), key);
        if (locked.replayed()) return locked;
        return bind(key, applyApproval(locked.order(), adminId, why, "APPROVED_WITH_NOTE", Set.of("TRANSFER_REPORTED")));
    }

    @Transactional
    public Order reject(UUID id, UUID adminId, String reason) {
        return reject(id, adminId, reason, null).order();
    }

    @Transactional
    public Review reject(UUID id, UUID adminId, String reason, String idempotencyKey) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Cần nhập lý do từ chối đối soát.");
        ReviewKey key = reviewKey(adminId, idempotencyKey, "reject", id, reason.trim());
        Review locked = lockForReview(id, Set.of("TRANSFER_REPORTED"), key);
        if (locked.replayed()) return locked;
        return bind(key, applyTerminal(locked.order(), adminId, reason.trim(), "REJECTED", "REJECTED", "TRANSFER_REPORTED"));
    }

    // ------------------------------------------------------------------------------------------------ transitions

    private Order applyApproval(Order order, UUID adminId, String note, String resolution, Set<String> from) {
        String placeholders = String.join(",", from.stream().map(s -> "?").toList());
        List<Object> args = new ArrayList<>(List.of(adminId, now(), note == null ? "" : note, resolution, now(), order.id()));
        args.addAll(from);
        List<String> previous = jdbc.queryForList("""
                UPDATE package_orders o SET status='APPROVED', reviewed_by=?, reviewed_at=?, review_note=NULLIF(?, ''), resolution=?,
                    updated_at=?, version=o.version+1
                FROM package_orders old WHERE o.id=? AND old.id=o.id AND o.status IN (%s)
                RETURNING old.status
                """.formatted(placeholders), String.class, args.toArray());
        if (previous.isEmpty()) throw ApiException.conflict("ORDER_STATE_CHANGED", "Đơn vừa được xử lý bởi thao tác khác.");
        // Only the request whose UPDATE changed the row grants the quota: exactly one effect.
        jdbc.update("""
                UPDATE users SET plan_code=?, plan_expires_at=GREATEST(COALESCE(plan_expires_at,?),?)+(?||' days')::interval,
                    listing_quota_remaining=listing_quota_remaining+?, updated_at=? WHERE id=?
                """, order.planCode(), now(), now(), order.durationDays(), order.quota(), now(), order.userId());
        jdbc.update("INSERT INTO invoices(id,invoice_number,order_id,user_id,amount_vnd) VALUES(?,?,?,?,?)", UUID.randomUUID(),
                "INV-" + LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))) + "-" + order.reference(), order.id(), order.userId(), order.amountVnd());
        event(order.id(), "APPROVED", previous.get(0), "APPROVED", adminId, note, Map.of("resolution", resolution));
        String message = "Tài khoản đã được nâng lên gói " + order.planName() + " và có thêm " + order.quota() + " lượt đăng tin.";
        notifications.notify(order.userId(), "PLAN_UPGRADED", "Nâng cấp thành công", message);
        emailUser(order, "[Nhà Đất Chuẩn] Đã kích hoạt gói " + order.planName(), message, "APPROVED");
        return load(order.id(), null, true);
    }

    private Order applyTerminal(Order order, UUID adminId, String note, String status, String resolution, String from) {
        int changed = jdbc.update("""
                UPDATE package_orders SET status=?, reviewed_by=?, reviewed_at=?, review_note=?, resolution=?, updated_at=?, version=version+1
                WHERE id=? AND status=?
                """, status, adminId, now(), note, resolution, now(), order.id(), from);
        if (changed == 0) throw ApiException.conflict("ORDER_STATE_CHANGED", "Đơn vừa được xử lý bởi thao tác khác.");
        event(order.id(), status, from, status, adminId, note, Map.of("resolution", resolution));
        String message = "REFUNDED".equals(status)
                ? "Khoản chuyển cho yêu cầu " + order.reference() + " đã được hoàn lại ngoài hệ thống. " + note
                : "Yêu cầu " + order.reference() + " không được duyệt: " + note;
        notifications.notify(order.userId(), "REFUNDED".equals(status) ? "PAYMENT_REFUNDED" : "PAYMENT_REJECTED",
                "REFUNDED".equals(status) ? "Đã hoàn tiền" : "Thanh toán cần kiểm tra lại", message);
        emailUser(order, "[Nhà Đất Chuẩn] Cập nhật yêu cầu thanh toán " + order.reference(), message, status);
        return load(order.id(), null, true);
    }

    /**
     * Row lock first, then (under it) the replay check, then the state check: a retry with the same Idempotency-Key
     * after the first attempt committed gets that result back (no second effect), even though the state moved on.
     */
    private Review lockForReview(UUID id, Set<String> allowed, ReviewKey key) {
        List<String> status = jdbc.queryForList("SELECT status FROM package_orders WHERE id=? FOR UPDATE", String.class, id);
        if (status.isEmpty()) throw ApiException.notFound("ORDER_NOT_FOUND", "Không tìm thấy yêu cầu thanh toán.");
        if (key != null) {
            List<Object[]> previous = jdbc.query("""
                    SELECT request_hash, resource_id, response_snapshot FROM api_idempotency_keys
                    WHERE scope=? AND idempotency_key=? AND """ + KEY_ALIVE,
                    (rs, n) -> new Object[]{rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3)}, key.scope(), key.key());
            if (!previous.isEmpty()) {
                if (!key.requestHash().equals(previous.get(0)[0]) || !id.equals(previous.get(0)[1])) {
                    throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key này đã dùng cho một thao tác khác.");
                }
                // The answer the keyed request gave, not the order as it is now (another admin may have acted since).
                String snapshot = (String) previous.get(0)[2];
                return new Review(snapshot == null ? load(id, null, true) : readOrder(snapshot), true);
            }
        }
        if (!allowed.contains(status.get(0))) throw stateChanged(status.get(0));
        return new Review(load(id, null, true), false);
    }

    /** A key is alive for 24 h; legacy rows without {@code expires_at} count from {@code created_at} (like lead keys). */
    private static final String KEY_ALIVE = " COALESCE(expires_at, created_at + interval '24 hours') > now()";
    /** Binds the key, or takes over an expired one (the purge job may not have removed it yet); a live key is left alone. */
    private static final String REUSE_EXPIRED_KEY = """
            ON CONFLICT (scope, idempotency_key) DO UPDATE SET request_hash = EXCLUDED.request_hash, resource_id = EXCLUDED.resource_id,
                created_at = now(), expires_at = EXCLUDED.expires_at, response_snapshot = EXCLUDED.response_snapshot
            WHERE COALESCE(api_idempotency_keys.expires_at, api_idempotency_keys.created_at + interval '24 hours') <= now()
            """;

    private record ReceiptPayload(long amount, String reference, String note) {}

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Order readOrder(String snapshot) {
        try {
            return json.readValue(snapshot, Order.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ApiException stateChanged(String status) {
        return ApiException.conflict("ORDER_STATE_CHANGED", "Đơn đang ở trạng thái " + status + ", không thể thực hiện thao tác này.");
    }

    private record ReviewKey(String scope, String key, String requestHash) {}

    /** Scoped to the admin (another admin's key never replays) and bound to the action, the order and its payload. */
    private static ReviewKey reviewKey(UUID adminId, String idempotencyKey, String action, UUID orderId, String payload) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key == null) return null;
        if (key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key tối đa 128 ký tự chữ, số, . _ : -");
        }
        return new ReviewKey("billing-review:" + adminId, key, AuthService.sha256(action + "|" + orderId + "|" + payload));
    }

    private Review bind(ReviewKey key, Order order) {
        if (key != null) {
            int bound = jdbc.update("""
                    INSERT INTO api_idempotency_keys(scope,idempotency_key,request_hash,resource_id,expires_at,response_snapshot)
                    VALUES(?,?,?,?, now() + interval '24 hours', ?)
                    """ + REUSE_EXPIRED_KEY, key.scope(), key.key(), key.requestHash(), order.id(), writeJson(order));
            // The same key raced on another order: roll this one back rather than leave an effect the key does not name.
            if (bound == 0) throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key này vừa được dùng cho một thao tác khác.");
        }
        return new Review(order, false);
    }

    // ------------------------------------------------------------------------------------------------ reads & helpers

    private static final String ADMIN_SELECT = """
            SELECT o.id,o.user_id,u.full_name,u.email,o.plan_code,o.plan_name_snapshot,o.amount_vnd,o.transfer_reference,o.status,o.created_at,
                   o.user_reported_at,o.reviewed_at,o.review_note,o.received_amount_vnd,o.received_reference,o.exception_reason,o.resolution,
                   rv.full_name AS reviewer_name
            FROM package_orders o JOIN users u ON u.id=o.user_id LEFT JOIN users rv ON rv.id=o.reviewed_by
            """;

    private Map<String, Long> counts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        jdbc.query("SELECT status, count(*) FROM package_orders WHERE status IN ('TRANSFER_REPORTED','EXCEPTION','CREATED') GROUP BY status",
                rs -> { counts.put(rs.getString(1), rs.getLong(2)); });
        return counts;
    }

    private List<OrderEvent> events(UUID orderId, boolean admin) {
        return jdbc.query("""
                SELECT e.id,e.type,e.from_status,e.to_status,e.actor_id,u.full_name,e.note,e.data::text,e.created_at
                FROM package_order_events e LEFT JOIN users u ON u.id=e.actor_id
                WHERE e.order_id=? ORDER BY e.created_at, e.id LIMIT 100
                """, (rs, n) -> new OrderEvent(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                admin ? rs.getObject(5, UUID.class) : null, admin ? rs.getString(6) : null, rs.getString(7), admin ? rs.getString(8) : null,
                rs.getTimestamp(9).toInstant()), orderId);
    }

    private void event(UUID orderId, String type, String from, String to, UUID actorId, String note, Map<String, ?> data) {
        String payload;
        try { payload = data == null ? null : json.writeValueAsString(data); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        jdbc.update("""
                INSERT INTO package_order_events(id,order_id,type,from_status,to_status,actor_id,note,data,created_at)
                VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb),?)
                """, UUID.randomUUID(), orderId, type, from, to, actorId, note == null ? null : note.length() > 500 ? note.substring(0, 500) : note,
                payload, now());
    }

    private Order load(UUID id, UUID userId, boolean admin) {
        String where = admin ? " WHERE o.id=?" : " WHERE o.id=? AND o.user_id=?";
        return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM package_orders o" + where, (r, n) -> map(r), admin ? new Object[]{id} : new Object[]{id, userId})
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Không tìm thấy yêu cầu thanh toán."));
    }

    private Order map(ResultSet r) throws SQLException {
        String status = r.getString("status");
        String qr = null;
        if (r.getString("bank_bin_snapshot") != null && "CREATED".equals(status)) {
            qr = "https://img.vietqr.io/image/" + r.getString("bank_bin_snapshot") + "-" + r.getString("account_number_snapshot")
                    + "-compact2.png?amount=" + r.getLong("amount_vnd") + "&addInfo=" + enc(r.getString("transfer_reference"))
                    + "&accountName=" + enc(r.getString("account_name_snapshot"));
        }
        return new Order(r.getObject("id", UUID.class), r.getObject("user_id", UUID.class), r.getString("plan_code"), r.getLong("amount_vnd"),
                r.getString("transfer_reference"), status, instant(r, "created_at"), qr, r.getString("plan_name_snapshot"),
                r.getInt("quota_snapshot"), r.getInt("duration_days_snapshot"), r.getString("bank_bin_snapshot"),
                r.getString("account_number_snapshot"), r.getString("account_name_snapshot"), instant(r, "user_reported_at"),
                instant(r, "reviewed_at"), r.getString("review_note"), r.getString("exception_reason"), r.getString("resolution"),
                instant(r, "updated_at"), r.getString("invoice_number"));
    }

    private AdminOrder mapAdmin(ResultSet r) throws SQLException {
        return new AdminOrder(r.getObject("id", UUID.class), r.getObject("user_id", UUID.class), r.getString("full_name"), r.getString("email"),
                r.getString("plan_code"), r.getString("plan_name_snapshot"), r.getLong("amount_vnd"), r.getString("transfer_reference"),
                r.getString("status"), instant(r, "created_at"), instant(r, "user_reported_at"), instant(r, "reviewed_at"),
                r.getString("review_note"), r.getObject("received_amount_vnd") == null ? null : r.getLong("received_amount_vnd"),
                r.getString("received_reference"), r.getString("exception_reason"), r.getString("resolution"), r.getString("reviewer_name"));
    }

    private static String label(String reason) {
        return switch (reason) {
            case "AMOUNT_MISMATCH" -> "số tiền không khớp";
            case "REFERENCE_MISMATCH" -> "nội dung chuyển khoản không khớp";
            default -> "số tiền và nội dung chuyển khoản không khớp";
        };
    }

    private static String normalize(String value) { return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT); }

    private static String trimOrNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private Timestamp now() { return Timestamp.from(clock.instant()); }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp t = r.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }

    /**
     * Queued with the transfer report (same transaction) and sent once by the job worker, with retries if SMTP is down.
     * Never fatal: an unusable stored address (saved before it was validated) is logged and counted by the outbox, and the
     * TRANSFER_REPORTED transition commits regardless; the order is in the admin reconciliation queue either way.
     */
    private void emailAdmin(Order o) {
        BankSettings b = bank();
        if (b == null || b.adminEmail() == null || b.adminEmail().isBlank()) return;
        MailOutbox.Result result = mailOutbox.tryEnqueue(MailMessage.text(b.adminEmail().trim(), "[Nhà Đất Chuẩn] Có thanh toán chờ đối soát " + o.reference(),
                "Mã đối soát: " + o.reference() + "\nSố tiền: " + o.amountVnd()
                        + " VND\nVui lòng mở trang quản trị để kiểm tra và xác nhận.",
                "BILLING_TRANSFER_REPORTED", "billing-transfer-reported:" + o.id()));
        if (result.outcome() == MailOutbox.Outcome.REJECTED) {
            log.warn("billing_admin_notification_skipped order={} reason={}", o.id(), result.rejectionReason().orElse("unknown"));
        }
    }

    private void emailUser(Order o, String subject, String body, String status) {
        String email = jdbc.query("SELECT email FROM users WHERE id=?", (rs, n) -> rs.getString(1), o.userId()).stream().findFirst().orElse(null);
        if (email == null || email.isBlank()) return;
        MailOutbox.Result result = mailOutbox.tryEnqueue(MailMessage.text(email.trim(), subject,
                body + "\n\nĐây là thanh toán cho gói dịch vụ đăng tin trên Nhà Đất Chuẩn, không phải tiền đặt cọc bất động sản.",
                "BILLING_ORDER_" + status, "billing-order:" + o.id() + ":" + status));
        if (result.outcome() == MailOutbox.Outcome.REJECTED) {
            log.warn("billing_user_notification_skipped order={} reason={}", o.id(), result.rejectionReason().orElse("unknown"));
        }
    }

    public record Plan(String code, String name, long priceVnd, int quota, int durationDays, String description) {}

    public record BankSettings(String bankBin, String bankName, String accountNumber, String accountName, String adminEmail, long version) {}

    /** Snapshot of the plan and bank at order time; {@code qrUrl} only while the order waits for a transfer. */
    public record Order(UUID id, UUID userId, String planCode, long amountVnd, String reference, String status, Instant createdAt,
                        String qrUrl, String planName, int quota, int durationDays, String bankBinSnapshot, String accountNumberSnapshot,
                        String accountNameSnapshot, Instant reportedAt, Instant reviewedAt, String reviewNote, String exceptionReason,
                        String resolution, Instant updatedAt, String invoiceNumber) {}

    public record CreateResult(Order order, boolean created) {}

    /** An admin review result; {@code replayed} when an Idempotency-Key retry got the committed result back. */
    public record Review(Order order, boolean replayed) {}

    public record OrderPage(List<Order> items, int page, int size, long total) {}

    public record OrderEvent(UUID id, String type, String fromStatus, String toStatus, UUID actorId, String actorName, String note,
                             String data, Instant createdAt) {}

    public record OrderDetail(Order order, List<OrderEvent> events) {}

    public record AdminOrder(UUID id, UUID userId, String customerName, String customerEmail, String planCode, String planName, long amountVnd,
                             String reference, String status, Instant createdAt, Instant reportedAt, Instant reviewedAt, String reviewNote,
                             Long receivedAmountVnd, String receivedReference, String exceptionReason, String resolution, String reviewerName) {}

    public record AdminOrderPage(List<AdminOrder> items, int page, int size, long total, Map<String, Long> counts) {}
}
