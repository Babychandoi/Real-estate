package com.company.bds.media;

import com.company.bds.shared.jobs.JobQueue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backfill of images uploaded before the pipeline (ADMIN only via {@code /api/v2/admin/**}). Each call enqueues up to
 * {@code limit} LEGACY public objects that have no pending job, oldest first, so repeated calls make progress and a
 * crashed run can simply be repeated. {@code GET} reports counts per state and the queue backlog.
 */
@RestController
@RequestMapping("/api/v2/admin/media/backfill")
@ConditionalOnProperty(name = "app.media.storage-enabled", havingValue = "true")
class MediaBackfillController {
    private final JdbcTemplate jdbc;
    private final JobQueue jobs;

    MediaBackfillController(JdbcTemplate jdbc, JobQueue jobs) {
        this.jdbc = jdbc;
        this.jobs = jobs;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> enqueue(@RequestParam(defaultValue = "200") int limit) {
        int bounded = Math.max(1, Math.min(limit, 500));
        List<String> keys = jdbc.queryForList("""
                SELECT m.object_key FROM media_objects m
                WHERE m.processing_state = 'LEGACY' AND m.visibility = 'PUBLIC'
                  AND NOT EXISTS (SELECT 1 FROM background_jobs j
                                  WHERE j.queue = ? AND j.dedupe_key = m.object_key
                                    AND j.completed_at IS NULL AND j.dead_lettered_at IS NULL)
                ORDER BY m.created_at, m.object_key
                LIMIT ?
                """, String.class, MediaVariantJobHandler.QUEUE, bounded);
        for (String key : keys) jobs.enqueue(MediaVariantJobHandler.QUEUE, key, Map.of("objectKey", key), null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enqueued", keys.size());
        body.putAll(status());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> progress() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status());
    }

    private Map<String, Object> status() {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String state : List.of("LEGACY", "PENDING", "READY", "FAILED")) counts.put(state, 0L);
        jdbc.query("""
                SELECT processing_state, count(*) FROM media_objects WHERE visibility = 'PUBLIC' GROUP BY processing_state
                """, rs -> { counts.put(rs.getString(1), rs.getLong(2)); });
        Long queued = jdbc.queryForObject("""
                SELECT count(*) FROM background_jobs
                WHERE queue = ? AND completed_at IS NULL AND dead_lettered_at IS NULL
                """, Long.class, MediaVariantJobHandler.QUEUE);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("states", counts);
        result.put("queued", queued == null ? 0 : queued);
        return result;
    }
}
