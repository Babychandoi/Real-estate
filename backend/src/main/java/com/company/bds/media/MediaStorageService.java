package com.company.bds.media;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.media.storage-enabled", havingValue = "true")
public class MediaStorageService {
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final String PUBLIC_MEDIA_PREFIX = "/api/v1/public/media/";
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/avif", "avif");

    private final MinioClient minio;
    private final JdbcTemplate jdbc;
    private final String bucket;
    private final ClamAvScanner antivirus;

    public MediaStorageService(JdbcTemplate jdbc, ClamAvScanner antivirus,
            @Value("${app.media.endpoint}") String endpoint,
            @Value("${app.media.access-key}") String accessKey,
            @Value("${app.media.secret-key}") String secretKey,
            @Value("${app.media.bucket}") String bucket) {
        if (accessKey.isBlank() || secretKey.length() < 12) {
            throw new IllegalStateException("MinIO chưa được cấu hình credential an toàn.");
        }
        this.jdbc = jdbc;
        this.antivirus = antivirus;
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
            String objectKey = UUID.randomUUID() + "." + EXTENSIONS.get(detectedType);
            minio.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .contentType(detectedType).stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L).build());
            try {
                jdbc.update("INSERT INTO media_objects(object_key,owner_id,content_type,size_bytes,visibility) VALUES (?,?,?,?,?)",
                        objectKey, ownerId, detectedType, bytes.length, kycPrivate ? "KYC_PRIVATE" : "PUBLIC");
            } catch (RuntimeException dbError) {
                minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
                throw dbError;
            }
            return new UploadedImage(objectKey, (kycPrivate ? "/api/v1/media/kyc/" : PUBLIC_MEDIA_PREFIX) + objectKey, detectedType, (long) bytes.length);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể lưu ảnh vào MinIO. Vui lòng thử lại.", ex);
        }
    }

    public StoredImage read(String objectKey) {
        MediaMetadata metadata;
        try {
            metadata = jdbc.queryForObject("SELECT content_type,size_bytes FROM media_objects WHERE object_key=? AND visibility='PUBLIC'",
                    (rs, row) -> new MediaMetadata(rs.getString(1), rs.getLong(2)), objectKey);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Ảnh không tồn tại.");
        }
        try {
            GetObjectResponse stream = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return new StoredImage(stream, metadata.contentType(), metadata.sizeBytes());
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể đọc ảnh từ MinIO.", ex);
        }
    }

    public StoredImage readPrivate(UUID actorId, boolean privileged, String objectKey) {
        var rows=jdbc.query("SELECT content_type,size_bytes FROM media_objects WHERE object_key=? AND visibility='KYC_PRIVATE' AND (owner_id=? OR ?)",(rs,n)->new MediaMetadata(rs.getString(1),rs.getLong(2)),objectKey,actorId,privileged);
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
        if (attached != null && attached > 0) {
            throw new IllegalStateException("Ảnh đang thuộc lịch sử revision và không thể xóa vật lý.");
        }
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            jdbc.update("DELETE FROM media_objects WHERE object_key=? AND owner_id=?", objectKey, ownerId);
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

    @Scheduled(cron = "${app.media.orphan-cleanup-cron:0 30 3 * * *}")
    public void cleanupOrphans() {
        var keys = jdbc.queryForList("""
                SELECT object_key FROM media_objects m
                WHERE m.created_at < CURRENT_TIMESTAMP - INTERVAL '24 hours'
                  AND NOT EXISTS (SELECT 1 FROM listing_media lm WHERE lm.media_url=CONCAT('/api/v1/public/media/',m.object_key))
                ORDER BY m.created_at LIMIT 100
                """, String.class);
        for (String key : keys) {
            try {
                minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
                jdbc.update("DELETE FROM media_objects WHERE object_key=?", key);
            } catch (Exception ignored) {
                // Retry on the next scheduled pass; never delete metadata if object deletion failed.
            }
        }
    }

    boolean bucketAvailable() {
        try { return minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()); }
        catch (Exception ex) { return false; }
    }

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

    public record UploadedImage(String objectKey, String url, String contentType, long sizeBytes) {}
    public record StoredImage(GetObjectResponse stream, String contentType, long sizeBytes) {}
    private record MediaMetadata(String contentType, long sizeBytes) {}
}
