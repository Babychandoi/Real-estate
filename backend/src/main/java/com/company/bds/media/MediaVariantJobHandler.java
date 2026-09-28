package com.company.bds.media;

import com.company.bds.shared.jobs.ClaimedJob;
import com.company.bds.shared.jobs.JobBatchResult;
import com.company.bds.shared.jobs.JobHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Queue {@value #QUEUE} (F14.2): turns a PENDING upload (or a LEGACY object picked by the backfill) into a sanitised,
 * upright master plus WebP variants and placeholder, then marks it READY. Idempotent (a READY/FAILED/deleted object is
 * a no-op) and sequential (small batches; {@link ImageProcessor} admits one image per JVM at a time) to bound memory.
 * Input that cannot be decoded is marked FAILED once — retrying cannot help — and is never served publicly.
 */
@Component
@ConditionalOnProperty(name = "app.media.storage-enabled", havingValue = "true")
class MediaVariantJobHandler implements JobHandler {
    static final String QUEUE = "media-variants";
    private static final Logger log = LoggerFactory.getLogger(MediaVariantJobHandler.class);

    private final MediaStorageService storage;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MeterRegistry meters;

    MediaVariantJobHandler(MediaStorageService storage, JdbcTemplate jdbc, TransactionTemplate tx, MeterRegistry meters) {
        this.storage = storage;
        this.jdbc = jdbc;
        this.tx = tx;
        this.meters = meters;
    }

    @jakarta.annotation.PostConstruct
    void checkEncoder() {
        if (!ImageProcessor.webpSelfTest()) {
            log.error("WebP encoder unavailable (native libwebp not loadable on this platform): media jobs will retry and fail");
        }
    }

    @Override public String queue() { return QUEUE; }

    @Override public int batchSize() { return 4; }

    @Override public Duration lease() { return Duration.ofMinutes(5); }

    @Override public int maxAttempts() { return 6; }

    @Override
    public JobBatchResult handle(List<ClaimedJob> jobs) {
        JobBatchResult.Builder result = JobBatchResult.builder();
        for (ClaimedJob job : jobs) {
            String key = job.text("objectKey");
            try {
                String outcome = process(key);
                meters.counter("bds.media.processed", "outcome", outcome).increment();
                result.succeed(job);
            } catch (Exception ex) {
                meters.counter("bds.media.processed", "outcome", "retry").increment();
                log.warn("media variant job failed for {}: {}", key, ex.toString());
                result.fail(job, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        return result.build();
    }

    String process(String key) throws Exception {
        if (key == null || !MediaKeys.isOriginal(key)) return "invalid";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT content_type FROM media_objects
                WHERE object_key = ? AND visibility = 'PUBLIC' AND processing_state IN ('PENDING', 'LEGACY')
                """, key);
        if (rows.isEmpty()) return "skipped";   // already processed, failed, deleted or private
        String contentType = (String) rows.get(0).get("content_type");
        ImageProcessor.Result image;
        try {
            if ("image/avif".equals(contentType)) throw new ImageProcessor.UnsupportedImageException("AVIF");
            image = ImageProcessor.process(storage.getBytes(key), contentType);
        } catch (ImageProcessor.UnsupportedImageException unsupported) {
            jdbc.update("""
                    UPDATE media_objects SET processing_state = 'FAILED', processing_error = ?, processed_at = now()
                    WHERE object_key = ? AND processing_state IN ('PENDING', 'LEGACY')
                    """, truncate(unsupported.getMessage()), key);
            return "failed";
        }
        for (ImageProcessor.Variant variant : image.variants()) {
            storage.putObject(MediaKeys.variantKey(key, variant.width()), variant.bytes(), "image/webp");
        }
        // Overwrites the original: from now on the stored bytes carry no EXIF/GPS and are upright.
        storage.putObject(key, image.master(), image.contentType());
        Boolean committed = tx.execute(status -> {
            int updated = jdbc.update("""
                    UPDATE media_objects SET processing_state = 'READY', width = ?, height = ?, dominant_color = ?,
                           lqip = ?, size_bytes = ?, processed_at = now(), processing_error = NULL
                    WHERE object_key = ? AND processing_state IN ('PENDING', 'LEGACY')
                    """, image.width(), image.height(), image.dominantColor(), image.lqip(), image.master().length, key);
            if (updated == 0) return false;
            jdbc.update("DELETE FROM media_variants WHERE object_key = ?", key);
            for (ImageProcessor.Variant variant : image.variants()) {
                jdbc.update("""
                        INSERT INTO media_variants (variant_key, object_key, width, height, format, size_bytes)
                        VALUES (?, ?, ?, ?, 'webp', ?)
                        """, MediaKeys.variantKey(key, variant.width()), key, variant.width(), variant.height(),
                        variant.bytes().length);
            }
            return true;
        });
        if (!Boolean.TRUE.equals(committed)) {
            // Deleted (or processed elsewhere) meanwhile: do not leave objects behind that no row points to.
            boolean exists = !jdbc.queryForList("SELECT 1 FROM media_objects WHERE object_key = ?", key).isEmpty();
            if (!exists) {
                for (ImageProcessor.Variant variant : image.variants()) {
                    storage.removeQuietly(MediaKeys.variantKey(key, variant.width()));
                }
                storage.removeQuietly(key);
            }
            return "skipped";
        }
        return "ready";
    }

    private static String truncate(String message) {
        String value = message == null ? "unsupported" : message;
        return value.length() <= 300 ? value : value.substring(0, 300);
    }
}
