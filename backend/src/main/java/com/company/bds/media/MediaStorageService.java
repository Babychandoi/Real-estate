package com.company.bds.media;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import com.company.bds.shared.jobs.JobQueue;
import com.company.bds.shared.scheduling.ScheduledTaskLock;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MinIO media: upload (antivirus, magic bytes, header-only dimension check), the S1 image pipeline hand-off (queue
 * {@link MediaVariantJobHandler#QUEUE}), public/signed/private reads and deletion.
 *
 * <p><b>Public serving policy (F14.4, R-3):</b> {@code /api/v1/public/media/<key>} (original or WebP variant) is served
 * only while the image is {@code READY} (sanitised) or {@code LEGACY} (uploaded before the pipeline, until the backfill
 * processes it) <em>and</em> referenced by a publicly visible listing (ACTIVE, APPROVED public revision, seller ACTIVE),
 * the avatar of an ACTIVE user or the cover of a published CMS article. Everything else — drafts, pending edits,
 * hidden/locked/expired listings, banned sellers, images still being processed — answers 404 at once. Owners and staff
 * see such images through {@link MediaUrlSigner signed URLs}. Hiding never deletes objects (revisions are immutable and
 * unhide restores access); only unreferenced objects are removed by the orphan sweep.
 */
@Service
@ConditionalOnProperty(name = "app.media.storage-enabled", havingValue = "true")
public class MediaStorageService {
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final String PUBLIC_MEDIA_PREFIX = MediaKeys.PUBLIC_PREFIX;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/avif", "avif");

    /** Referenced by something public; evaluated for the row {@code m} of {@code media_objects}. */
    static final String PUBLICLY_REFERENCED = """
            (EXISTS (SELECT 1 FROM listing_media lm
                     JOIN listing_revisions r ON r.id = lm.revision_id AND r.status = 'APPROVED'
                     JOIN listings l ON l.public_revision_id = r.id AND l.id = r.listing_id AND l.status = 'ACTIVE'
                     JOIN users u ON u.id = l.owner_id AND u.status = 'ACTIVE'
                     WHERE lm.media_url = '/api/v1/public/media/' || m.object_key)
             OR EXISTS (SELECT 1 FROM users au
                        WHERE au.avatar_media_url = '/api/v1/public/media/' || m.object_key AND au.status = 'ACTIVE')
             OR EXISTS (SELECT 1 FROM cms_articles a
                        JOIN cms_article_revisions cr ON cr.id = a.published_revision_id
                        WHERE a.status = 'PUBLISHED' AND cr.cover_image_url = '/api/v1/public/media/' || m.object_key))
            """;

    private final MinioClient minio;
    private final JdbcTemplate jdbc;
    private final String bucket;
    private final ClamAvScanner antivirus;
    private final ScheduledTaskLock taskLock;
    private final JobQueue jobs;
    private final MediaUrlSigner signer;

    public MediaStorageService(JdbcTemplate jdbc, ClamAvScanner antivirus, ScheduledTaskLock taskLock, JobQueue jobs,
            MediaUrlSigner signer,
            @Value("${app.media.endpoint}") String endpoint,
            @Value("${app.media.access-key}") String accessKey,
            @Value("${app.media.secret-key}") String secretKey,
            @Value("${app.media.bucket}") String bucket) {
        if (accessKey.isBlank() || secretKey.length() < 12) {
            throw new IllegalStateException("MinIO chưa được cấu hình credential an toàn.");
        }
        this.jdbc = jdbc;
        this.antivirus = antivirus;
        this.taskLock = taskLock;
        this.jobs = jobs;
        this.signer = signer;
        this.bucket = bucket;
        this.minio = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @PostConstruct
    void ensureBucket() {
        try {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể khởi tạo bucket MinIO.", ex);
        }
    }

    @Transactional
    public UploadedImage upload(UUID ownerId, MultipartFile file) {
        return upload(ownerId, file, false);
    }

    @Transactional
    public UploadedImage uploadKyc(UUID ownerId, MultipartFile file) { return upload(ownerId, file, true); }

    private UploadedImage upload(UUID ownerId, MultipartFile file, boolean kycPrivate) {
        try {
            if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("Ảnh phải có dung lượng từ 1 byte đến 10 MB.");
            }
            byte[] bytes = file.getBytes();
            antivirus.assertClean(bytes);
            String detectedType = detectContentType(bytes);
            String declaredType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            if (!detectedType.equals(declaredType)) {
                throw new IllegalArgumentException("Nội dung file không khớp định dạng ảnh khai báo.");
            }
            if (!kycPrivate) {
                if ("image/avif".equals(detectedType)) {
                    // Cannot be decoded on the JVM, hence neither resized nor stripped of EXIF/GPS: refused.
                    throw new IllegalArgumentException("Ảnh tin đăng chưa hỗ trợ AVIF. Vui lòng dùng JPEG, PNG hoặc WebP.");
                }
                ImageProcessor.probe(bytes, detectedType);   // header only: corrupt or > 50 MP is refused now
            }
            String objectKey = UUID.randomUUID() + "." + EXTENSIONS.get(detectedType);
            putObject(objectKey, bytes, detectedType);
            try {
                // KYC documents stay untouched (verification fidelity) and are never public: no pipeline.
                jdbc.update("""
                        INSERT INTO media_objects(object_key,owner_id,content_type,size_bytes,visibility,processing_state)
                        VALUES (?,?,?,?,?,?)
                        """, objectKey, ownerId, detectedType, bytes.length,
                        kycPrivate ? "KYC_PRIVATE" : "PUBLIC", kycPrivate ? "LEGACY" : "PENDING");
                if (!kycPrivate) jobs.enqueue(MediaVariantJobHandler.QUEUE, objectKey, Map.of("objectKey", objectKey), null);
            } catch (RuntimeException dbError) {
                removeQuietly(objectKey);
                throw dbError;
            }
            if (kycPrivate) {
                return new UploadedImage(objectKey, "/api/v1/media/kyc/" + objectKey, detectedType, (long) bytes.length,
                        null, null);
            }
            MediaUrlSigner.SignedUrl preview = signer.sign(objectKey);
            return new UploadedImage(objectKey, PUBLIC_MEDIA_PREFIX + objectKey, detectedType, (long) bytes.length,
                    preview.url(), preview.expiresAt());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể lưu ảnh vào MinIO. Vui lòng thử lại.", ex);
        }
    }

    /** Public read (original or variant) under the serving policy above; anything else is "not found". */
    public StoredImage read(String objectKey) {
        MediaMetadata metadata = MediaKeys.isVariant(objectKey)
                ? first(jdbc.query("""
                        SELECT 'image/' || v.format, v.size_bytes, m.object_key FROM media_variants v
                        JOIN media_objects m ON m.object_key = v.object_key
                        WHERE v.variant_key = ? AND m.visibility = 'PUBLIC' AND m.processing_state = 'READY'
                          AND """ + PUBLICLY_REFERENCED,
                        MediaStorageService::metadata, objectKey))
                : first(jdbc.query("""
                        SELECT m.content_type, m.size_bytes, m.object_key FROM media_objects m
                        WHERE m.object_key = ? AND m.visibility = 'PUBLIC' AND m.processing_state IN ('READY','LEGACY')
                          AND """ + PUBLICLY_REFERENCED,
                        MediaStorageService::metadata, objectKey));
        if (metadata == null) throw new MediaNotFoundException();
        return open(objectKey, metadata);
    }

    /**
     * Read through a signed URL (issued to the owner or staff only): any state except KYC documents, which keep their own
     * password-gated, logged endpoint.
     */
    public StoredImage readSigned(String objectKey, long exp, String sig) {
        if (!signer.verify(objectKey, exp, sig)) throw new MediaNotFoundException();
        MediaMetadata metadata = MediaKeys.isVariant(objectKey)
                ? first(jdbc.query("""
                        SELECT 'image/' || v.format, v.size_bytes, m.object_key FROM media_variants v
                        JOIN media_objects m ON m.object_key = v.object_key
                        WHERE v.variant_key = ? AND m.visibility = 'PUBLIC'
                        """, MediaStorageService::metadata, objectKey))
                : first(jdbc.query("""
                        SELECT content_type, size_bytes, object_key FROM media_objects
                        WHERE object_key = ? AND visibility = 'PUBLIC'
                        """, MediaStorageService::metadata, objectKey));
        if (metadata == null) throw new MediaNotFoundException();
        return open(objectKey, metadata);
    }

    /**
     * Signed URLs for the given media URLs (originals or variants, at most 50): only for objects the actor owns, or any
     * public-bucket object when the actor is staff. External URLs, KYC documents and foreign objects are left out.
     */
    public SignedUrls signForViewer(UUID actorId, boolean staff, List<String> urls) {
        if (urls == null || urls.isEmpty()) return new SignedUrls(Map.of(), null);
        if (urls.size() > 50) throw new IllegalArgumentException("Tối đa 50 ảnh mỗi lần.");
        Map<String, String> keyOfUrl = new java.util.LinkedHashMap<>();
        Map<String, String> parentOfKey = new java.util.HashMap<>();
        for (String url : urls) {
            String key = MediaKeys.keyOfPublicUrl(url);
            if (key == null) continue;
            keyOfUrl.put(url, key);
            parentOfKey.put(key, key);
        }
        if (keyOfUrl.isEmpty()) return new SignedUrls(Map.of(), null);
        List<String> variantKeys = parentOfKey.keySet().stream().filter(MediaKeys::isVariant).toList();
        if (!variantKeys.isEmpty()) {
            jdbc.query("SELECT variant_key, object_key FROM media_variants WHERE variant_key = ANY (?)",
                    rs -> { parentOfKey.put(rs.getString(1), rs.getString(2)); },
                    (Object) variantKeys.toArray(String[]::new));
        }
        java.util.Set<String> allowed = new java.util.HashSet<>(jdbc.queryForList("""
                SELECT object_key FROM media_objects
                WHERE object_key = ANY (?) AND visibility = 'PUBLIC' AND (owner_id = ? OR ?)
                """, String.class, parentOfKey.values().stream().distinct().toArray(String[]::new), actorId, staff));
        Map<String, String> signed = new java.util.LinkedHashMap<>();
        Instant expiresAt = null;
        for (Map.Entry<String, String> entry : keyOfUrl.entrySet()) {
            String key = entry.getValue();
            String parent = parentOfKey.get(key);
            if (MediaKeys.isVariant(key) && parent.equals(key)) continue;   // unknown variant
            if (!allowed.contains(parent)) continue;
            MediaUrlSigner.SignedUrl url = signer.sign(key);
            signed.put(entry.getKey(), url.url());
            expiresAt = url.expiresAt();
        }
        return new SignedUrls(signed, expiresAt);
    }

    public StoredImage readPrivate(UUID actorId, boolean privileged, String objectKey) {
        var rows=jdbc.query("SELECT content_type,size_bytes,object_key FROM media_objects WHERE object_key=? AND visibility='KYC_PRIVATE' AND (owner_id=? OR ?)",MediaStorageService::metadata,objectKey,actorId,privileged);
        if(rows.isEmpty())throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem tài liệu định danh.");
        try { return new StoredImage(minio.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build()),rows.get(0).contentType(),rows.get(0).sizeBytes()); }
        catch(Exception ex){throw new IllegalStateException("Không thể đọc tài liệu định danh.",ex);}
    }

    @Transactional
    public void delete(UUID ownerId, String objectKey) {
        Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM media_objects WHERE object_key=? AND owner_id=?",
                Integer.class, objectKey, ownerId);
        if (owned == null || owned == 0) throw new IllegalArgumentException("Không tìm thấy ảnh thuộc tài khoản hiện tại.");
        Integer attached = jdbc.queryForObject("SELECT COUNT(*) FROM listing_media WHERE media_url=?",
                Integer.class, PUBLIC_MEDIA_PREFIX + objectKey);
        Integer avatarAttached = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE avatar_media_url=?",
                Integer.class, PUBLIC_MEDIA_PREFIX + objectKey);
        if ((attached != null && attached > 0) || (avatarAttached != null && avatarAttached > 0)) {
            throw new IllegalStateException("Ảnh đang thuộc lịch sử revision và không thể xóa vật lý.");
        }
        String privateUrl = "/api/v1/media/kyc/" + objectKey;
        Integer kycAttached = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_kyc_profiles
                WHERE id_card_front_url=? OR id_card_back_url=? OR selfie_url=?
                """, Integer.class, privateUrl, privateUrl, privateUrl);
        Integer verificationAttached = jdbc.queryForObject("""
                SELECT COUNT(*) FROM listing_verifications
                WHERE document_urls IS NOT NULL AND document_urls LIKE ?
                """, Integer.class, "%" + privateUrl + "%");
        if ((kycAttached != null && kycAttached > 0)
                || (verificationAttached != null && verificationAttached > 0)) {
            throw new IllegalStateException("Tài liệu đang được gắn với hồ sơ xác minh nên không thể xóa.");
        }
        try {
            removeWithVariants(objectKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể xóa ảnh khỏi MinIO.", ex);
        }
    }

    public void validateOwnership(UUID ownerId, List<String> mediaUrls) {
        if (mediaUrls == null) return;
        for (String mediaUrl : mediaUrls) {
            if (mediaUrl == null || !mediaUrl.startsWith(PUBLIC_MEDIA_PREFIX)) continue;
            String objectKey = mediaUrl.substring(PUBLIC_MEDIA_PREFIX.length());
            Integer owned = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM media_objects WHERE object_key=? AND owner_id=?",
                    Integer.class, objectKey, ownerId);
            if (owned == null || owned == 0) {
                throw new IllegalArgumentException("Ảnh MinIO không thuộc tài khoản hiện tại hoặc không tồn tại.");
            }
        }
    }

    public void validateKycOwnership(UUID ownerId, List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            throw new IllegalArgumentException("Cần cung cấp đầy đủ các ảnh định danh bắt buộc.");
        }
        String prefix = "/api/v1/media/kyc/";
        for (String mediaUrl : mediaUrls) {
            if (mediaUrl == null || !mediaUrl.startsWith(prefix)) {
                throw new IllegalArgumentException("Đường dẫn ảnh định danh không hợp lệ.");
            }
            String objectKey = mediaUrl.substring(prefix.length());
            Integer owned = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM media_objects
                    WHERE object_key=? AND owner_id=? AND visibility='KYC_PRIVATE'
                    """, Integer.class, objectKey, ownerId);
            if (owned == null || owned == 0) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Ảnh định danh không tồn tại hoặc thuộc tài khoản khác.");
            }
        }
    }

    /** Daily cleanup; with several instances only the one holding the task lock runs it. */
    @Scheduled(cron = "${app.media.orphan-cleanup-cron:0 30 3 * * *}")
    public void cleanupOrphans() {
        taskLock.runExclusive("media-orphan-cleanup", Duration.ofMinutes(30), Duration.ofMinutes(5), this::removeOrphans);
    }

    void removeOrphans() {
        var keys = jdbc.queryForList("""
                SELECT object_key FROM media_objects m
                WHERE m.created_at < CURRENT_TIMESTAMP - INTERVAL '24 hours'
                  AND NOT EXISTS (SELECT 1 FROM listing_media lm WHERE lm.media_url=CONCAT('/api/v1/public/media/',m.object_key))
                  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.avatar_media_url=CONCAT('/api/v1/public/media/',m.object_key))
                  AND NOT EXISTS (SELECT 1 FROM cms_article_revisions cr WHERE cr.cover_image_url=CONCAT('/api/v1/public/media/',m.object_key))
                  AND NOT EXISTS (
                    SELECT 1 FROM user_kyc_profiles k
                    WHERE k.id_card_front_url=CONCAT('/api/v1/media/kyc/',m.object_key)
                       OR k.id_card_back_url=CONCAT('/api/v1/media/kyc/',m.object_key)
                       OR k.selfie_url=CONCAT('/api/v1/media/kyc/',m.object_key)
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM listing_verifications v
                    WHERE v.document_urls IS NOT NULL
                      AND v.document_urls LIKE CONCAT('%/api/v1/media/kyc/',m.object_key,'%')
                  )
                ORDER BY m.created_at LIMIT 100
                """, String.class);
        for (String key : keys) {
            try {
                removeWithVariants(key);
            } catch (Exception ignored) {
                // Retry on the next scheduled pass; never delete metadata if object deletion failed.
            }
        }
    }

    /** Removes the variants, then the original, then the metadata (variant rows cascade). */
    private void removeWithVariants(String objectKey) throws Exception {
        for (String variant : jdbc.queryForList("SELECT variant_key FROM media_variants WHERE object_key=?",
                String.class, objectKey)) {
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(variant).build());
        }
        minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        jdbc.update("DELETE FROM media_objects WHERE object_key=?", objectKey);
    }

    // ---- storage primitives used by the pipeline -------------------------------------------------------------------

    void putObject(String key, byte[] bytes, String contentType) throws Exception {
        minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                .contentType(contentType).stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L).build());
    }

    byte[] getBytes(String key) throws Exception {
        try (GetObjectResponse stream = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return stream.readNBytes((int) MAX_IMAGE_BYTES + 1);
        }
    }

    void removeQuietly(String key) {
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ignored) {
            // best effort; the orphan sweep only knows rows, so an object without a row may remain
        }
    }

    boolean bucketAvailable() {
        try { return minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()); }
        catch (Exception ex) { return false; }
    }

    private StoredImage open(String objectKey, MediaMetadata metadata) {
        try {
            GetObjectResponse stream = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
            // The object's own length: the pipeline may have replaced the bytes a moment before the row was updated.
            String length = stream.headers().get("Content-Length");
            long size = length != null && length.matches("[0-9]{1,12}") ? Long.parseLong(length) : metadata.sizeBytes();
            return new StoredImage(stream, metadata.contentType(), size);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể đọc ảnh từ MinIO.", ex);
        }
    }

    private static MediaMetadata metadata(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new MediaMetadata(rs.getString(1), rs.getLong(2));
    }

    @Nullable
    private static <T> T first(List<T> rows) { return rows.isEmpty() ? null : rows.get(0); }

    static String detectContentType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), png)) return "image/png";
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return "image/webp";
        if (bytes.length >= 12 && ascii(bytes, 4, "ftyp") && (ascii(bytes, 8, "avif") || ascii(bytes, 8, "avis"))) return "image/avif";
        throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPEG, PNG, WebP hoặc AVIF hợp lệ.");
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        for (int i = 0; i < expected.length(); i++) if (bytes[offset + i] != (byte) expected.charAt(i)) return false;
        return true;
    }

    /**
     * {@code previewUrl}: a signed URL (owner only) that shows the image right away, before it is published and processed.
     */
    public record UploadedImage(String objectKey, String url, String contentType, long sizeBytes,
                                @Nullable String previewUrl, @Nullable Instant previewExpiresAt) {}
    public record StoredImage(GetObjectResponse stream, String contentType, long sizeBytes) {}
    public record SignedUrls(Map<String, String> urls, @Nullable Instant expiresAt) {}
    private record MediaMetadata(String contentType, long sizeBytes) {}

    /** Not served: unknown, not public (any more), or a bad signature. Always 404 so nothing is revealed. */
    public static final class MediaNotFoundException extends RuntimeException {
        public MediaNotFoundException() { super("Ảnh không tồn tại."); }
    }
}
