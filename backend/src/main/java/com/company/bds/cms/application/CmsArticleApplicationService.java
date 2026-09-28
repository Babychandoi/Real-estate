package com.company.bds.cms.application;

import com.company.bds.cms.application.port.ArticleStore;
import com.company.bds.cms.application.port.ArticleStore.AdminRow;
import com.company.bds.cms.application.port.ArticleStore.PublicArticle;
import com.company.bds.cms.application.port.ArticleStore.PublicPage;
import com.company.bds.cms.domain.model.Article;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleRevision;
import com.company.bds.cms.domain.model.ArticleStatus;
import com.company.bds.cms.domain.model.RevisionContent;
import com.company.bds.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * CMS workflow (audit P-07): DRAFT → SUBMITTED → (PUBLISHED now | SCHEDULED at {@code publishAt}) or REJECTED; an edit
 * of anything that left DRAFT is a new revision; publishing a newer revision SUPERSEDES the previous public one;
 * unpublishing archives the article (the public URL then answers 410). Every transition locks the article row.
 */
@Service
@Transactional
public class CmsArticleApplicationService {
    public static final Duration PREVIEW_TTL = Duration.ofHours(24);
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Draft(String slug, ArticleCategory category, RevisionContent content) {}

    public record ArticleDetail(Article article, List<ArticleRevision> revisions) {
        public ArticleRevision latest() { return revisions.isEmpty() ? null : revisions.get(0); }
    }

    public record AdminPage(List<AdminRow> items, long total, int page, int size) {}

    public record PreviewLink(String token, Instant expiresAt) {}

    /** Result of a public lookup: the live article, or GONE (was public; 410), or absent (404). */
    public record PublicLookup(PublicArticle article, boolean gone) {}

    private final ArticleStore store;
    private final Clock clock;

    public CmsArticleApplicationService(ArticleStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- authoring

    public ArticleDetail create(Draft draft, UUID actor) {
        String slug = draft.slug() == null ? "" : draft.slug().trim().toLowerCase();
        if (slug.length() > 200 || !SLUG.matcher(slug).matches()) {
            throw ApiException.badRequest("CMS_SLUG_INVALID", "Đường dẫn chỉ gồm chữ thường không dấu, số và dấu gạch ngang.");
        }
        if (draft.category() == null) throw ApiException.badRequest("CMS_CATEGORY_REQUIRED", "Chọn chuyên mục bài viết.");
        if (store.slugExists(slug)) throw ApiException.conflict("CMS_SLUG_TAKEN", "Đường dẫn này đã được dùng cho bài viết khác.");
        Instant now = clock.instant();
        UUID articleId = UUID.randomUUID();
        store.insertArticle(articleId, slug, draft.category(), now);
        store.insertRevision(UUID.randomUUID(), articleId, 1, clean(draft.content()), actor, now);
        return detail(articleId);
    }

    /** New DRAFT revision with this content (the way to edit anything that is submitted, scheduled or public). */
    public ArticleDetail newRevision(UUID articleId, RevisionContent content, UUID actor) {
        Article article = locked(articleId);
        store.insertRevision(UUID.randomUUID(), article.id(), store.nextRevisionNumber(article.id()), clean(content), actor, clock.instant());
        return detail(articleId);
    }

    public ArticleDetail updateDraft(UUID articleId, UUID revisionId, RevisionContent content) {
        locked(articleId);
        ArticleRevision revision = revisionOf(articleId, revisionId);
        if (revision.status() != ArticleStatus.DRAFT) {
            throw ApiException.conflict("CMS_REVISION_IMMUTABLE", "Bản đã nộp duyệt không sửa được; hãy tạo bản sửa mới.");
        }
        store.updateDraft(revisionId, clean(content));
        return detail(articleId);
    }

    public ArticleRevision submit(UUID articleId, UUID revisionId) {
        Article article = locked(articleId);
        ArticleRevision revision = revisionOf(articleId, revisionId);
        if (revision.status() != ArticleStatus.DRAFT && revision.status() != ArticleStatus.REJECTED) {
            throw ApiException.conflict("CMS_INVALID_TRANSITION", "Chỉ bản nháp mới nộp duyệt được.");
        }
        if (revision.status() == ArticleStatus.REJECTED) {
            throw ApiException.conflict("CMS_REVISION_IMMUTABLE", "Bản bị từ chối không nộp lại được; hãy tạo bản sửa mới.");
        }
        Instant now = clock.instant();
        store.setRevisionStatus(revisionId, ArticleStatus.SUBMITTED, null, now, null, null);
        if (article.status() == ArticleStatus.DRAFT || article.status() == ArticleStatus.REJECTED) {
            store.updateArticle(article.id(), ArticleStatus.SUBMITTED, article.publishedRevisionId(), article.scheduledRevisionId(),
                    article.scheduledPublishAt(), article.publishedAt(), article.firstPublishedAt(), article.unpublishedAt(), now);
        }
        return store.findRevision(revisionId).orElseThrow();
    }

    /**
     * Approves a SUBMITTED revision: published now when {@code publishAt} is null or not in the future, otherwise
     * SCHEDULED (replacing an earlier schedule, which goes back to SUBMITTED).
     */
    public ArticleRevision approve(UUID articleId, UUID revisionId, UUID reviewer, Instant publishAt) {
        Article article = locked(articleId);
        ArticleRevision revision = revisionOf(articleId, revisionId);
        if (revision.status() != ArticleStatus.SUBMITTED) {
            throw ApiException.conflict("CMS_INVALID_TRANSITION", "Chỉ bản đang chờ duyệt mới được duyệt.");
        }
        Instant now = clock.instant();
        if (publishAt != null && publishAt.isAfter(now.plus(Duration.ofDays(366)))) {
            throw ApiException.badRequest("CMS_SCHEDULE_TOO_FAR", "Chỉ hẹn giờ xuất bản trong vòng 12 tháng.");
        }
        if (article.scheduledRevisionId() != null && !article.scheduledRevisionId().equals(revisionId)) {
            store.setRevisionStatus(article.scheduledRevisionId(), ArticleStatus.SUBMITTED, null, null, null, null);
        }
        if (publishAt != null && publishAt.isAfter(now)) {
            store.setRevisionStatus(revisionId, ArticleStatus.SCHEDULED, null, null, now, reviewer.toString());
            ArticleStatus status = article.status() == ArticleStatus.PUBLISHED ? ArticleStatus.PUBLISHED : ArticleStatus.SUBMITTED;
            store.updateArticle(article.id(), status, article.publishedRevisionId(), revisionId, publishAt,
                    article.publishedAt(), article.firstPublishedAt(), article.unpublishedAt(), now);
        } else {
            store.setRevisionStatus(revisionId, ArticleStatus.PUBLISHED, null, null, now, reviewer.toString());
            goLive(article, revisionId, now, now);
        }
        return store.findRevision(revisionId).orElseThrow();
    }

    public ArticleRevision reject(UUID articleId, UUID revisionId, String reason, UUID reviewer) {
        Article article = locked(articleId);
        ArticleRevision revision = revisionOf(articleId, revisionId);
        if (revision.status() != ArticleStatus.SUBMITTED && revision.status() != ArticleStatus.SCHEDULED) {
            throw ApiException.conflict("CMS_INVALID_TRANSITION", "Chỉ bản đang chờ duyệt hoặc đang hẹn giờ mới từ chối được.");
        }
        String note = reason == null ? "" : reason.trim();
        if (note.length() < 5) throw ApiException.badRequest("CMS_REASON_REQUIRED", "Nêu lý do từ chối (ít nhất 5 ký tự).");
        Instant now = clock.instant();
        store.setRevisionStatus(revisionId, ArticleStatus.REJECTED, note.substring(0, Math.min(1000, note.length())), null, now,
                reviewer.toString());
        boolean wasScheduled = revisionId.equals(article.scheduledRevisionId());
        ArticleStatus status = article.status() == ArticleStatus.SUBMITTED && article.publishedRevisionId() == null
                ? ArticleStatus.REJECTED : article.status();
        store.updateArticle(article.id(), status, article.publishedRevisionId(),
                wasScheduled ? null : article.scheduledRevisionId(), wasScheduled ? null : article.scheduledPublishAt(),
                article.publishedAt(), article.firstPublishedAt(), article.unpublishedAt(), now);
        return store.findRevision(revisionId).orElseThrow();
    }

    /** Cancels a pending schedule: the revision goes back to SUBMITTED. */
    public ArticleDetail cancelSchedule(UUID articleId) {
        Article article = locked(articleId);
        if (article.scheduledRevisionId() == null) throw ApiException.conflict("CMS_NOT_SCHEDULED", "Bài viết không có lịch xuất bản.");
        Instant now = clock.instant();
        store.setRevisionStatus(article.scheduledRevisionId(), ArticleStatus.SUBMITTED, null, null, null, null);
        store.updateArticle(article.id(), article.status(), article.publishedRevisionId(), null, null, article.publishedAt(),
                article.firstPublishedAt(), article.unpublishedAt(), now);
        return detail(articleId);
    }

    /** Takes the article off the public site (its URL answers 410 from now on); a pending schedule is cancelled. */
    public ArticleDetail unpublish(UUID articleId) {
        Article article = locked(articleId);
        Instant now = clock.instant();
        boolean live = article.status() == ArticleStatus.PUBLISHED
                || (article.scheduledPublishAt() != null && !article.scheduledPublishAt().isAfter(now));
        if (!live) throw ApiException.conflict("CMS_NOT_PUBLISHED", "Bài viết chưa được xuất bản.");
        if (article.scheduledRevisionId() != null) {
            store.setRevisionStatus(article.scheduledRevisionId(), ArticleStatus.SUBMITTED, null, null, null, null);
        }
        Instant first = article.firstPublishedAt() != null ? article.firstPublishedAt() : article.scheduledPublishAt();
        store.updateArticle(article.id(), ArticleStatus.ARCHIVED, article.publishedRevisionId(), null, null, article.publishedAt(),
                first, now, now);
        return detail(articleId);
    }

    /** Promotes scheduled revisions whose time has come (the public read already shows them; this makes it durable). */
    public int publishDue(int limit) {
        Instant now = clock.instant();
        int promoted = 0;
        for (UUID id : store.dueArticles(now, limit)) {
            Article article = store.lockArticle(id).orElse(null);
            if (article == null || article.scheduledRevisionId() == null || article.scheduledPublishAt().isAfter(now)) continue;
            UUID revisionId = article.scheduledRevisionId();
            store.setRevisionStatus(revisionId, ArticleStatus.PUBLISHED, null, null, null, null);
            goLive(article, revisionId, article.scheduledPublishAt(), now);
            promoted++;
        }
        store.purgeExpiredPreviewTokens(now);
        return promoted;
    }

    private void goLive(Article article, UUID revisionId, Instant liveAt, Instant now) {
        if (article.publishedRevisionId() != null && !article.publishedRevisionId().equals(revisionId)) {
            store.setRevisionStatus(article.publishedRevisionId(), ArticleStatus.SUPERSEDED, null, null, null, null);
        }
        boolean clearsSchedule = revisionId.equals(article.scheduledRevisionId());
        store.updateArticle(article.id(), ArticleStatus.PUBLISHED, revisionId,
                clearsSchedule ? null : article.scheduledRevisionId(), clearsSchedule ? null : article.scheduledPublishAt(),
                liveAt, article.firstPublishedAt() != null ? article.firstPublishedAt() : liveAt, null, now);
    }

    // ---------------------------------------------------------------- preview

    public PreviewLink mintPreview(UUID articleId, UUID revisionId, UUID actor) {
        revisionOf(articleId, revisionId);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = clock.instant().plus(PREVIEW_TTL);
        store.insertPreviewToken(UUID.randomUUID(), sha256(token), revisionId, actor, expires);
        return new PreviewLink(token, expires);
    }

    @Transactional(readOnly = true)
    public Optional<ArticleDetail> preview(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) return Optional.empty();
        return store.previewRevision(sha256(token), clock.instant())
                .flatMap(store::findRevision)
                .flatMap(revision -> store.findArticle(revision.articleId())
                        .map(article -> new ArticleDetail(article, List.of(revision))));
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public ArticleDetail detail(UUID articleId) {
        Article article = store.findArticle(articleId)
                .orElseThrow(() -> ApiException.notFound("CMS_ARTICLE_NOT_FOUND", "Không tìm thấy bài viết."));
        return new ArticleDetail(article, store.revisions(articleId, 100));
    }

    @Transactional(readOnly = true)
    public AdminPage adminPage(ArticleCategory category, ArticleStatus status, int page, int size) {
        int safePage = Math.max(0, Math.min(page, 1000));
        int safeSize = Math.max(1, Math.min(size, 100));
        return new AdminPage(store.adminPage(category, status, safePage, safeSize), store.adminCount(category, status), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public PublicLookup publicBySlug(String slug) {
        if (slug == null || slug.length() > 255) return new PublicLookup(null, false);
        Optional<PublicArticle> live = store.publicBySlug(slug, clock.instant());
        if (live.isPresent()) return new PublicLookup(live.get(), false);
        boolean gone = store.findBySlug(slug).map(Article::wasEverPublic).orElse(false);
        return new PublicLookup(null, gone);
    }

    @Transactional(readOnly = true)
    public PublicPage publicPage(ArticleCategory category, int page, int size) {
        return store.publicPage(category, clock.instant(), Math.max(0, Math.min(page, 500)), Math.max(1, Math.min(size, 50)));
    }

    @Transactional(readOnly = true)
    public List<ArticleStore.SitemapEntry> sitemapEntries(int limit) {
        return store.sitemapEntries(clock.instant(), limit);
    }

    // ---------------------------------------------------------------- helpers

    private Article locked(UUID articleId) {
        return store.lockArticle(articleId)
                .orElseThrow(() -> ApiException.notFound("CMS_ARTICLE_NOT_FOUND", "Không tìm thấy bài viết."));
    }

    private ArticleRevision revisionOf(UUID articleId, UUID revisionId) {
        ArticleRevision revision = store.findRevision(revisionId)
                .orElseThrow(() -> ApiException.notFound("CMS_REVISION_NOT_FOUND", "Không tìm thấy phiên bản bài viết."));
        if (!revision.articleId().equals(articleId)) {
            throw ApiException.notFound("CMS_REVISION_NOT_FOUND", "Phiên bản không thuộc bài viết này.");
        }
        return revision;
    }

    static RevisionContent clean(RevisionContent c) {
        if (c == null) throw ApiException.badRequest("CMS_CONTENT_REQUIRED", "Thiếu nội dung bài viết.");
        String title = required(c.title(), 500, "CMS_TITLE_REQUIRED", "Nhập tiêu đề (tối đa 500 ký tự).");
        String author = required(c.authorName(), 255, "CMS_AUTHOR_REQUIRED", "Nhập tên tác giả hoặc ban biên tập.");
        String html = CmsHtmlSanitizer.sanitize(c.contentHtml());
        if (html == null || CmsHtmlSanitizer.text(html).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CMS_BODY_REQUIRED", "Nội dung bài viết không được để trống.");
        }
        String sourceUrl = optionalUrl(c.sourceUrl(), false, "CMS_SOURCE_URL_INVALID", "Đường dẫn nguồn phải bắt đầu bằng https:// hoặc http://.");
        String cover = optionalUrl(c.coverImageUrl(), true, "CMS_COVER_URL_INVALID", "Ảnh bìa phải là ảnh đã tải lên hoặc đường dẫn https://.");
        String canonical = optional(c.canonicalUrl(), 500);
        if (canonical != null && !canonical.startsWith("/") && !canonical.startsWith("https://")) {
            throw ApiException.badRequest("CMS_CANONICAL_INVALID", "Canonical phải là đường dẫn nội bộ (/...) hoặc https://.");
        }
        return new RevisionContent(title, optional(c.summary(), 2000), html, cover, author, optional(c.legalReference(), 500),
                optional(c.metaDescription(), 320), canonical, optional(c.sourceName(), 255), sourceUrl);
    }

    private static String required(String value, int max, String code, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) throw ApiException.badRequest(code, message);
        return trimmed;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static String optionalUrl(String value, boolean allowInternalMedia, String code, String message) {
        String url = optional(value, 1000);
        if (url == null) return null;
        boolean ok = url.startsWith("https://") || url.startsWith("http://")
                || (allowInternalMedia && url.startsWith("/api/v1/public/media/"));
        if (!ok || url.chars().anyMatch(ch -> ch < 0x20 || ch == '"' || ch == '<' || ch == '>')) throw ApiException.badRequest(code, message);
        return url;
    }

    static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
