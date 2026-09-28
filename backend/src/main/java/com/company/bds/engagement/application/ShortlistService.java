package com.company.bds.engagement.application;

import com.company.bds.engagement.application.port.SavedListingStorePort;
import com.company.bds.engagement.application.port.ShortlistStorePort;
import com.company.bds.engagement.application.port.ShortlistStorePort.Access;
import com.company.bds.engagement.application.port.ShortlistStorePort.Shared;
import com.company.bds.notification.NotificationRequest;
import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Shared shortlists (audit P-02: "shortlist chia sẻ có quyền và ngừng thông báo").
 *
 * <p>Access: OWNER (everything), EDITOR (add/remove listings), VIEWER (read). Sharing creates a link with a random
 * 256-bit token (only its SHA-256 is stored) that carries a role; a signed-in user who opens it joins with that role
 * (never lowering a role they already have). Rotating the link invalidates the old one; revoking stops sharing but keeps
 * current members. Someone holding the link without signing in sees the list's public listings and the owner's given
 * name only. Members get a SHORTLIST notification when listings are added or removed unless they muted the list. A
 * caller without access gets 404 (existence is not revealed).
 */
@Service
public class ShortlistService {
    public static final int MAX_OWNED = 20;
    public static final int MAX_ITEMS = 100;
    public static final int MAX_NAME = 80;
    private static final Set<String> ROLES = Set.of("VIEWER", "EDITOR");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShortlistStorePort store;
    private final SavedListingStorePort listings;
    private final RealtimeNotificationService notifications;
    private final Clock clock;

    public ShortlistService(ShortlistStorePort store, SavedListingStorePort listings,
                            RealtimeNotificationService notifications, Clock clock) {
        this.store = store;
        this.listings = listings;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public UUID create(UUID ownerId, String name) {
        String clean = name(name);
        listings.lockUser(ownerId);
        if (store.countOwned(ownerId) >= MAX_OWNED) {
            throw new ApiException(HttpStatus.CONFLICT, "SHORTLIST_LIMIT", "Bạn đã tạo tối đa " + MAX_OWNED + " danh sách.");
        }
        UUID id = UUID.randomUUID();
        store.create(id, ownerId, clean, clock.instant());
        return id;
    }

    @Transactional(readOnly = true)
    public List<ShortlistStorePort.Summary> mine(UUID userId) {
        return store.listFor(userId, 100);
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID shortlistId, UUID userId) {
        Access access = require(shortlistId, userId);
        return new Detail(access, store.items(shortlistId, MAX_ITEMS),
                store.members(shortlistId), store.countItems(shortlistId));
    }

    @Transactional
    public long rename(UUID shortlistId, UUID userId, String name, long expectedVersion) {
        Access access = requireOwner(shortlistId, userId);
        if (!store.rename(shortlistId, name(name), expectedVersion, clock.instant())) {
            throw ApiException.conflict("SHORTLIST_VERSION_CONFLICT",
                    "Danh sách vừa được thay đổi ở nơi khác. Hãy tải lại rồi thử lại.");
        }
        return access.version() + 1;
    }

    @Transactional
    public void delete(UUID shortlistId, UUID userId) {
        requireOwner(shortlistId, userId);
        store.delete(shortlistId);
    }

    @Transactional
    public void addItem(UUID shortlistId, UUID userId, UUID listingId) {
        Access access = requireEditor(shortlistId, userId);
        if (!listings.isPublic(listingId)) {
            throw ApiException.notFound("LISTING_NOT_FOUND", "Tin đăng không tồn tại hoặc không còn hiển thị.");
        }
        listings.lockUser(access.ownerId());
        if (store.countItems(shortlistId) >= MAX_ITEMS) {
            throw new ApiException(HttpStatus.CONFLICT, "SHORTLIST_FULL", "Danh sách đã có tối đa " + MAX_ITEMS + " tin.");
        }
        Instant now = clock.instant();
        if (store.addItem(shortlistId, listingId, userId, now)) {
            store.touch(shortlistId, now);
            tell(access, userId, "SHORTLIST_ITEM_ADDED", "Danh sách “" + access.name() + "” có tin mới",
                    "Một thành viên vừa thêm tin vào danh sách chia sẻ.");
        }
    }

    @Transactional
    public void removeItem(UUID shortlistId, UUID userId, UUID listingId) {
        Access access = requireEditor(shortlistId, userId);
        if (store.removeItem(shortlistId, listingId)) {
            store.touch(shortlistId, clock.instant());
            tell(access, userId, "SHORTLIST_ITEM_REMOVED", "Danh sách “" + access.name() + "” thay đổi",
                    "Một thành viên vừa bỏ một tin khỏi danh sách chia sẻ.");
        }
    }

    /** Creates or rotates the share link; returns the raw token (shown once; only its hash is kept). */
    @Transactional
    public String share(UUID shortlistId, UUID userId, String role) {
        requireOwner(shortlistId, userId);
        if (role == null || !ROLES.contains(role)) {
            throw ApiException.badRequest("INVALID_ROLE", "Quyền chia sẻ phải là VIEWER hoặc EDITOR.");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.setShare(shortlistId, hash(token), role, clock.instant());
        return token;
    }

    @Transactional
    public void revokeShare(UUID shortlistId, UUID userId) {
        requireOwner(shortlistId, userId);
        store.setShare(shortlistId, null, null, clock.instant());
    }

    /** Joins through a share link; returns the list id (the owner opening their own link just gets the id). */
    @Transactional
    public UUID join(String token, UUID userId) {
        Shared shared = shared(token).orElseThrow(ShortlistService::linkGone);
        if (!shared.ownerId().equals(userId)) {
            store.upsertMember(shared.shortlistId(), userId, shared.shareRole(), clock.instant());
            notifications.notify(new NotificationRequest(shared.ownerId(), "SHORTLIST_MEMBER_JOINED",
                    "Có người tham gia danh sách “" + shared.name() + "”",
                    "Một người đã mở liên kết chia sẻ và tham gia danh sách của bạn.",
                    "/saved?tab=shortlists&list=" + shared.shortlistId(),
                    "shortlist:" + shared.shortlistId() + ":joined:" + userId, false));
        }
        return shared.shortlistId();
    }

    /** Read-only view for a link holder (no sign-in): name, owner's given name, public listings. */
    @Transactional(readOnly = true)
    public PublicView publicView(String token) {
        Shared shared = shared(token).orElseThrow(ShortlistService::linkGone);
        List<UUID> ids = store.items(shared.shortlistId(), MAX_ITEMS).stream().map(ShortlistStorePort.Item::listingId).toList();
        return new PublicView(shared.name(), givenName(shared.ownerName()), shared.shareRole(), ids);
    }

    @Transactional
    public void setMemberRole(UUID shortlistId, UUID ownerId, UUID memberId, String role) {
        requireOwner(shortlistId, ownerId);
        if (role == null || !ROLES.contains(role)) throw ApiException.badRequest("INVALID_ROLE", "Quyền không hợp lệ.");
        if (!store.setMemberRole(shortlistId, memberId, role)) throw memberGone();
    }

    @Transactional
    public void removeMember(UUID shortlistId, UUID ownerId, UUID memberId) {
        requireOwner(shortlistId, ownerId);
        if (!store.removeMember(shortlistId, memberId)) throw memberGone();
    }

    @Transactional
    public void leave(UUID shortlistId, UUID userId) {
        Access access = require(shortlistId, userId);
        if ("OWNER".equals(access.role())) {
            throw ApiException.badRequest("OWNER_CANNOT_LEAVE", "Chủ danh sách không thể rời; hãy xóa danh sách nếu không cần nữa.");
        }
        store.removeMember(shortlistId, userId);
    }

    @Transactional
    public void mute(UUID shortlistId, UUID userId, boolean muted) {
        Access access = require(shortlistId, userId);
        if ("OWNER".equals(access.role())) {
            throw ApiException.badRequest("OWNER_MUTE_UNSUPPORTED",
                    "Chủ danh sách tắt thông báo danh sách trong mục Tùy chọn thông báo của tài khoản.");
        }
        store.setMuted(shortlistId, userId, muted);
    }

    private void tell(Access access, UUID actorId, String type, String title, String message) {
        for (UUID recipient : store.audience(access.shortlistId(), actorId)) {
            notifications.notify(new NotificationRequest(recipient, type, title, message,
                    "/saved?tab=shortlists&list=" + access.shortlistId(), null, false));
        }
    }

    private Optional<Shared> shared(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) return Optional.empty();
        return store.findByTokenHash(hash(token));
    }

    private Access require(UUID shortlistId, UUID userId) {
        return store.access(shortlistId, userId).orElseThrow(() ->
                ApiException.notFound("SHORTLIST_NOT_FOUND", "Không tìm thấy danh sách hoặc bạn không có quyền xem."));
    }

    private Access requireEditor(UUID shortlistId, UUID userId) {
        Access access = require(shortlistId, userId);
        if ("VIEWER".equals(access.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SHORTLIST_READ_ONLY", "Bạn chỉ có quyền xem danh sách này.");
        }
        return access;
    }

    private Access requireOwner(UUID shortlistId, UUID userId) {
        Access access = require(shortlistId, userId);
        if (!"OWNER".equals(access.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SHORTLIST_OWNER_ONLY", "Chỉ chủ danh sách mới làm được thao tác này.");
        }
        return access;
    }

    private static String name(String value) {
        String clean = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.length() > MAX_NAME) {
            throw ApiException.badRequest("INVALID_NAME", "Tên danh sách cần từ 1 đến " + MAX_NAME + " ký tự.");
        }
        return clean;
    }

    /** Vietnamese names end with the given name: "Nguyễn Văn An" → "An". */
    public static String givenName(String fullName) {
        if (fullName == null || fullName.isBlank()) return null;
        String[] parts = fullName.strip().split("\\s+");
        return parts[parts.length - 1];
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ApiException linkGone() {
        return ApiException.notFound("SHARE_LINK_INVALID", "Liên kết chia sẻ không còn hiệu lực. Hãy xin người chia sẻ liên kết mới.");
    }

    private static ApiException memberGone() {
        return ApiException.notFound("MEMBER_NOT_FOUND", "Không tìm thấy thành viên.");
    }

    public record Detail(Access access, List<ShortlistStorePort.Item> items, List<ShortlistStorePort.Member> members,
                         int itemCount) {}

    public record PublicView(String name, String ownerGivenName, String shareRole, List<UUID> listingIds) {}
}
