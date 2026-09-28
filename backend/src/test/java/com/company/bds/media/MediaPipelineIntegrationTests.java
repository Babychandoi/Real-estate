package com.company.bds.media;

import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.QueryCount;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * S1-MEDIA end to end on PostgreSQL + the shared test MinIO (bucket {@code s1-media-it}): upload → durable job →
 * sanitised master + WebP variants → resolver; public serving policy for drafts/hidden/banned; signed URLs; backfill.
 */
@BdsIntegrationTest(properties = {
        "app.media.storage-enabled=true",
        "app.media.endpoint=${BDS_TEST_MINIO_URL:http://127.0.0.1:59000}",
        "app.media.access-key=${BDS_TEST_MINIO_USER:bds-test-media}",
        "app.media.secret-key=${BDS_TEST_MINIO_PASSWORD:bds-test-media-only}",
        "app.media.bucket=s1-media-it",
        "app.media.signing-secret=s1-media-test-signing-secret-0123456789"})
class MediaPipelineIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobWorker worker;
    @Autowired MediaStorageService storage;
    @Autowired PublicImageResolver resolver;
    @Autowired MediaUrlSigner signer;

    @Test
    void uploadIsProcessedByTheJobIntoASanitisedUprightMasterWithVariants() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        String token = data.sessionFor(owner.id());
        JsonNode uploaded = upload(token, MediaTestImages.jpegWithExif(600, 400, 6), "image/jpeg");
        String url = uploaded.get("url").asText();
        String key = uploaded.get("objectKey").asText();
        assertThat(url).isEqualTo("/api/v1/public/media/" + key);
        assertThat(state(key)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM background_jobs WHERE queue='media-variants' AND dedupe_key=?",
                Long.class, key)).isEqualTo(1L);

        // Not public yet (not referenced, not processed); the owner previews through the signed URL at once.
        assertThat(fetch(get(url)).getResponse().getStatus()).isEqualTo(404);
        MvcResult preview = fetch(get(uploaded.get("previewUrl").asText()));
        assertThat(preview.getResponse().getStatus()).isEqualTo(200);
        assertThat(preview.getResponse().getHeader("Cache-Control")).contains("no-store");

        worker.drain(MediaVariantJobHandler.QUEUE);

        assertThat(state(key)).isEqualTo("READY");
        Map<String, Object> row = jdbc.queryForMap("SELECT width,height,dominant_color,lqip FROM media_objects WHERE object_key=?", key);
        assertThat(row.get("width")).isEqualTo(400);
        assertThat(row.get("height")).isEqualTo(600);
        assertThat((String) row.get("dominant_color")).matches("#[0-9a-f]{6}");
        assertThat((String) row.get("lqip")).startsWith("data:image/webp;base64,");
        assertThat(jdbc.queryForList("SELECT width FROM media_variants WHERE object_key=? ORDER BY width", Integer.class, key))
                .containsExactly(320, 400);

        byte[] master = storage.getBytes(key);
        assertThat(ImageMetadata.containsMetadata(master, "image/jpeg")).as("EXIF/GPS stripped").isFalse();
        BufferedImage upright = ImageIO.read(new ByteArrayInputStream(master));
        assertThat(upright.getWidth()).isEqualTo(400);
        assertThat(upright.getHeight()).isEqualTo(600);
        byte[] variant = storage.getBytes(MediaKeys.variantKey(key, 320));
        assertThat(new String(variant, 8, 4)).isEqualTo("WEBP");
        assertThat(ImageMetadata.containsMetadata(variant, "image/webp")).isFalse();

        // Idempotent: a second delivery of the same job is a no-op.
        assertThat(jdbc.update("UPDATE background_jobs SET completed_at=NULL WHERE queue='media-variants' AND dedupe_key=?", key)).isEqualTo(1);
        worker.drain(MediaVariantJobHandler.QUEUE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_variants WHERE object_key=?", Long.class, key)).isEqualTo(2L);
    }

    @Test
    void resolverReturnsSrcsetAndPlaceholderInOneQuery() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        String key = processedUpload(data.sessionFor(owner.id()), MediaTestImages.jpeg(2400, 1600));
        String external = "https://images.unsplash.com/photo-1";

        List<ImageDto> images = QueryCount.assertAtMost(1, () -> resolver.resolve(List.of(MediaKeys.publicUrl(key), external)));

        ImageDto image = images.get(0);
        assertThat(image.url()).isEqualTo(MediaKeys.publicUrl(key));
        assertThat(image.width()).isEqualTo(2048);
        assertThat(image.height()).isEqualTo(1365);
        assertThat(image.srcset()).extracting(ImageDto.Source::width).containsExactly(320, 640, 960, 1600);
        assertThat(image.srcset().get(0).url()).isEqualTo("/api/v1/public/media/" + key.substring(0, 36) + "__w320.webp");
        assertThat(image.placeholder().dominantColor()).matches("#[0-9a-f]{6}");
        assertThat(image.placeholder().lqip()).startsWith("data:image/webp;base64,");
        assertThat(images.get(1)).isEqualTo(ImageDto.urlOnly(external));
    }

    @Test
    void publicServingFollowsListingVisibility() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        String key = processedUpload(data.sessionFor(owner.id()), MediaTestImages.jpeg(1200, 800));
        String url = MediaKeys.publicUrl(key);
        String variantUrl = MediaKeys.publicUrl(MediaKeys.variantKey(key, 640));

        // Draft and submitted-but-not-approved listings never make the image public.
        data.listing(owner.id()).status("DRAFT").mediaUrls(List.of(url)).create();
        data.listing(owner.id()).status("PENDING_REVIEW").mediaUrls(List.of(url)).create();
        assertThat(status(get(url))).isEqualTo(404);
        assertThat(status(get(variantUrl))).isEqualTo(404);

        TestData.TestListing listing = data.listing(owner.id()).mediaUrls(List.of(url)).create();
        MvcResult served = fetch(get(url));
        assertThat(served.getResponse().getStatus()).isEqualTo(200);
        assertThat(served.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(served.getResponse().getHeader("Cache-Control")).contains("max-age=86400").contains("public")
                .doesNotContain("immutable");
        MvcResult variant = fetch(get(variantUrl));
        assertThat(variant.getResponse().getStatus()).isEqualTo(200);
        assertThat(variant.getResponse().getContentType()).isEqualTo("image/webp");
        assertThat(variant.getResponse().getContentAsByteArray().length).isEqualTo(
                jdbc.queryForObject("SELECT size_bytes FROM media_variants WHERE variant_key=?", Long.class,
                        MediaKeys.variantKey(key, 640)).intValue());

        for (String hidden : List.of("PAUSED", "LOCKED", "EXPIRED")) {
            jdbc.update("UPDATE listings SET status=? WHERE id=?", hidden, listing.id());
            assertThat(status(get(url))).as(hidden).isEqualTo(404);
            assertThat(status(get(variantUrl))).as(hidden).isEqualTo(404);
        }
        // Unhide restores access: nothing was deleted.
        jdbc.update("UPDATE listings SET status='ACTIVE' WHERE id=?", listing.id());
        assertThat(status(get(url))).isEqualTo(200);

        // A locked seller's images disappear at once too.
        jdbc.update("UPDATE users SET status='LOCKED' WHERE id=?", owner.id());
        assertThat(status(get(url))).isEqualTo(404);
        jdbc.update("UPDATE users SET status='ACTIVE' WHERE id=?", owner.id());

        // Avatars of active users are public.
        String avatar = processedUpload(data.sessionFor(owner.id()), MediaTestImages.png(300, 300));
        assertThat(status(get(MediaKeys.publicUrl(avatar)))).isEqualTo(404);
        jdbc.update("UPDATE users SET avatar_media_url=? WHERE id=?", MediaKeys.publicUrl(avatar), owner.id());
        assertThat(status(get(MediaKeys.publicUrl(avatar)))).isEqualTo(200);
    }

    @Test
    void signedUrlsAreIssuedOnlyToTheOwnerOrStaffAndCannotBeTamperedWith() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestUser stranger = data.user().role("BROKER").create();
        TestData.TestUser moderator = data.user().role("MODERATOR").create();
        String key = processedUpload(data.sessionFor(owner.id()), MediaTestImages.jpeg(800, 600));
        String url = MediaKeys.publicUrl(key);
        String variantUrl = MediaKeys.publicUrl(MediaKeys.variantKey(key, 320));
        String external = "https://images.unsplash.com/photo-1";
        String kyc = "/api/v1/media/kyc/" + UUID.randomUUID() + ".jpg";

        JsonNode mine = sign(data.sessionFor(owner.id()), List.of(url, variantUrl, external, kyc));
        assertThat(mine.get("urls").size()).isEqualTo(2);
        assertThat(mine.get("expiresAt").asText()).isNotBlank();
        String signedUrl = mine.get("urls").get(url).asText();
        assertThat(signedUrl).startsWith("/api/v1/media/signed/" + key + "?exp=");
        assertThat(status(get(signedUrl))).isEqualTo(200);
        MvcResult signedVariant = fetch(get(mine.get("urls").get(variantUrl).asText()));
        assertThat(signedVariant.getResponse().getContentType()).isEqualTo("image/webp");

        assertThat(sign(data.sessionFor(stranger.id()), List.of(url, variantUrl)).get("urls").size()).isZero();
        assertThat(sign(data.sessionFor(moderator.id()), List.of(url)).get("urls").has(url)).isTrue();
        assertThat(status(post("/api/v1/media/signed-urls").contentType(MediaType.APPLICATION_JSON)
                .content("{\"urls\":[\"" + url + "\"]}"))).isEqualTo(401);

        // Tampered signature, a signature moved to another key, a changed expiry: all 404.
        String sig = signedUrl.substring(signedUrl.indexOf("&sig=") + 5);
        String exp = signedUrl.substring(signedUrl.indexOf("?exp=") + 5, signedUrl.indexOf("&sig="));
        assertThat(status(get(signedUrl.replace("&sig=" + sig, "&sig=" + flip(sig))))).isEqualTo(404);
        String otherKey = processedUpload(data.sessionFor(owner.id()), MediaTestImages.jpeg(400, 300));
        assertThat(status(get("/api/v1/media/signed/" + otherKey + "?exp=" + exp + "&sig=" + sig))).isEqualTo(404);
        assertThat(status(get("/api/v1/media/signed/" + key + "?exp=" + (Long.parseLong(exp) + 60) + "&sig=" + sig))).isEqualTo(404);
        assertThat(status(get("/api/v1/media/signed/" + key))).isEqualTo(404);

        // KYC documents are never reachable through a signed URL, even with a valid signature for their key.
        String kycKey = UUID.randomUUID() + ".jpg";
        jdbc.update("INSERT INTO media_objects(object_key,owner_id,content_type,size_bytes,visibility) VALUES (?,?,?,?, 'KYC_PRIVATE')",
                kycKey, owner.id(), "image/jpeg", 10);
        assertThat(status(get(signer.sign(kycKey).url()))).isEqualTo(404);
    }

    @Test
    void invalidUploadsAreRefusedAndUndecodableObjectsNeverBecomePublic() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        String token = data.sessionFor(owner.id());
        byte[] avif = "0000ftypavif00000000".getBytes();
        assertThat(uploadStatus(token, avif, "image/avif")).isEqualTo(400);
        byte[] brokenJpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 4, 1, 2, 3};
        assertThat(uploadStatus(token, brokenJpeg, "image/jpeg")).isEqualTo(400);

        // An object that passed upload but fails in the pipeline ends FAILED (not retried forever) and is never served.
        String key = UUID.randomUUID() + ".jpg";
        storage.putObject(key, brokenJpeg, "image/jpeg");
        jdbc.update("INSERT INTO media_objects(object_key,owner_id,content_type,size_bytes,visibility,processing_state) VALUES (?,?,?,?,'PUBLIC','PENDING')",
                key, owner.id(), "image/jpeg", brokenJpeg.length);
        storageJob(key);
        worker.drain(MediaVariantJobHandler.QUEUE);
        assertThat(state(key)).isEqualTo("FAILED");
        data.listing(owner.id()).mediaUrls(List.of(MediaKeys.publicUrl(key))).create();
        assertThat(status(get(MediaKeys.publicUrl(key)))).isEqualTo(404);
    }

    @Test
    void backfillProcessesLegacyImagesWhichStayServedUntilThen() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestUser admin = data.user().role("ADMIN").create();
        String key = UUID.randomUUID() + ".jpg";
        byte[] legacy = MediaTestImages.jpegWithExif(640, 480, 8);
        storage.putObject(key, legacy, "image/jpeg");
        jdbc.update("INSERT INTO media_objects(object_key,owner_id,content_type,size_bytes,visibility,created_at) VALUES (?,?,?,?,'PUBLIC', now() - interval '3 years')",
                key, owner.id(), "image/jpeg", legacy.length);
        assertThat(state(key)).isEqualTo("LEGACY");
        data.listing(owner.id()).mediaUrls(List.of(MediaKeys.publicUrl(key))).create();
        assertThat(status(get(MediaKeys.publicUrl(key)))).as("legacy stays served").isEqualTo(200);
        assertThat(resolver.resolveOne(MediaKeys.publicUrl(key)).orElseThrow().srcset()).isEmpty();

        String adminToken = data.sessionFor(admin.id());
        assertThat(status(post("/api/v2/admin/media/backfill").header("Authorization", "Bearer " + data.sessionFor(owner.id()))))
                .isEqualTo(403);
        JsonNode first = json(fetch(post("/api/v2/admin/media/backfill?limit=500").header("Authorization", "Bearer " + adminToken)));
        assertThat(first.get("enqueued").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM background_jobs WHERE queue='media-variants' AND dedupe_key=? AND completed_at IS NULL",
                Long.class, key)).isEqualTo(1L);
        // A repeated call does not enqueue what is already queued.
        json(fetch(post("/api/v2/admin/media/backfill?limit=500").header("Authorization", "Bearer " + adminToken)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM background_jobs WHERE queue='media-variants' AND dedupe_key=?",
                Long.class, key)).isEqualTo(1L);

        worker.drain(MediaVariantJobHandler.QUEUE);

        assertThat(state(key)).isEqualTo("READY");
        byte[] master = storage.getBytes(key);
        assertThat(ImageMetadata.containsMetadata(master, "image/jpeg")).isFalse();
        assertThat(ImageIO.read(new ByteArrayInputStream(master)).getWidth()).isEqualTo(480);
        assertThat(resolver.resolveOne(MediaKeys.publicUrl(key)).orElseThrow().srcset()).isNotEmpty();
        JsonNode progress = json(fetch(get("/api/v2/admin/media/backfill").header("Authorization", "Bearer " + adminToken)));
        assertThat(progress.get("states").get("READY").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void deletingAnUnattachedImageRemovesItsVariants() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        String token = data.sessionFor(owner.id());
        String key = processedUpload(token, MediaTestImages.jpeg(700, 500));
        String variant = MediaKeys.variantKey(key, 320);
        assertThat(storage.getBytes(variant)).isNotEmpty();

        assertThat(status(delete("/api/v1/media/images/" + key).header("Authorization", "Bearer " + token))).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_variants WHERE object_key=?", Long.class, key)).isZero();
        assertThat(objectExists(variant)).isFalse();
        assertThat(objectExists(key)).isFalse();
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private String processedUpload(String token, byte[] jpegOrPng) throws Exception {
        String type = jpegOrPng[0] == (byte) 0x89 ? "image/png" : "image/jpeg";
        String key = upload(token, jpegOrPng, type).get("objectKey").asText();
        worker.drain(MediaVariantJobHandler.QUEUE);
        assertThat(state(key)).isEqualTo("READY");
        return key;
    }

    private JsonNode upload(String token, byte[] bytes, String type) throws Exception {
        MvcResult result = mvc.perform(multipart("/api/v1/media/images")
                .file(new MockMultipartFile("file", "photo", type, bytes))
                .header("Authorization", "Bearer " + token)).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private int uploadStatus(String token, byte[] bytes, String type) throws Exception {
        return mvc.perform(multipart("/api/v1/media/images").file(new MockMultipartFile("file", "photo", type, bytes))
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    private JsonNode sign(String token, List<String> urls) throws Exception {
        return json(fetch(post("/api/v1/media/signed-urls").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("urls", urls)))));
    }

    private void storageJob(String key) {
        jdbc.queryForList("SELECT bds_enqueue_job('media-variants', ?, jsonb_build_object('objectKey', ?::text))::text", key, key);
    }

    private boolean objectExists(String key) {
        try {
            storage.getBytes(key);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private String state(String key) {
        return jdbc.queryForObject("SELECT processing_state FROM media_objects WHERE object_key=?", String.class, key);
    }

    private int status(RequestBuilder request) throws Exception { return fetch(request).getResponse().getStatus(); }

    /** Performs the request and completes a streaming (async) response. */
    private MvcResult fetch(RequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        if (result.getRequest().isAsyncStarted()) result = mvc.perform(asyncDispatch(result)).andReturn();
        return result;
    }

    private static JsonNode json(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private static String flip(String sig) {
        char c = sig.charAt(0);
        return (c == 'A' ? 'B' : 'A') + sig.substring(1);
    }
}
