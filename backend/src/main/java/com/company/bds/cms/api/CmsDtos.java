package com.company.bds.cms.api;

import com.company.bds.cms.application.CmsArticleApplicationService.ArticleDetail;
import com.company.bds.cms.application.port.ArticleStore.AdminRow;
import com.company.bds.cms.application.port.ArticleStore.PublicArticle;
import com.company.bds.cms.domain.model.Article;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleRevision;
import com.company.bds.cms.domain.model.ArticleStatus;
import com.company.bds.cms.domain.model.RevisionContent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CMS request/response bodies (never JPA entities). Field names of v1 responses are kept for existing clients. */
public final class CmsDtos {
    private CmsDtos() {}

    public record RevisionRequest(String title, String summary, String contentHtml, String coverImageUrl, String authorName,
                                  String legalReference, String metaDescription, String canonicalUrl, String sourceName,
                                  String sourceUrl) {
        public RevisionContent toContent() {
            return new RevisionContent(title, summary, contentHtml, coverImageUrl, authorName, legalReference,
                    metaDescription, canonicalUrl, sourceName, sourceUrl);
        }
    }

    /** {@code publishAt} null or past = publish now. */
    public record ApproveRequest(Instant publishAt) {}

    public record PreviewLinkResponse(String token, String path, Instant expiresAt) {}

    public record RevisionResponse(UUID id, UUID articleId, int revisionNumber, String title, String summary,
                                   String contentHtml, String coverImageUrl, String authorName, String legalReference,
                                   String metaDescription, String canonicalUrl, String sourceName, String sourceUrl,
                                   ArticleStatus status, String rejectionReason, Instant createdAt, Instant submittedAt,
                                   Instant reviewedAt, String reviewedBy) {
        public static RevisionResponse of(ArticleRevision r) {
            return new RevisionResponse(r.id(), r.articleId(), r.revisionNumber(), r.title(), r.summary(), r.contentHtml(),
                    r.coverImageUrl(), r.authorName(), r.legalReference(), r.metaDescription(), r.canonicalUrl(),
                    r.sourceName(), r.sourceUrl(), r.status(), r.rejectionReason(), r.createdAt(), r.submittedAt(),
                    r.reviewedAt(), r.reviewedBy());
        }
    }

    /** Admin view of an article (the public path of a published article is {@code /tin-tuc/<slug>}). */
    public record ArticleResponse(UUID id, String slug, ArticleCategory category, ArticleStatus status,
                                  UUID publishedRevisionId, UUID scheduledRevisionId, Instant scheduledPublishAt,
                                  Instant publishedAt, Instant firstPublishedAt, Instant unpublishedAt, Instant createdAt,
                                  Instant updatedAt, String publicPath, int revisionCount, RevisionResponse currentRevision,
                                  List<RevisionResponse> revisions) {
        static ArticleResponse of(Article a, ArticleRevision latest, int revisionCount, List<ArticleRevision> revisions) {
            return new ArticleResponse(a.id(), a.slug(), a.category(), a.status(), a.publishedRevisionId(),
                    a.scheduledRevisionId(), a.scheduledPublishAt(), a.publishedAt(), a.firstPublishedAt(),
                    a.unpublishedAt(), a.createdAt(), a.updatedAt(), "/tin-tuc/" + a.slug(), revisionCount,
                    latest == null ? null : RevisionResponse.of(latest),
                    revisions == null ? null : revisions.stream().map(RevisionResponse::of).toList());
        }

        public static ArticleResponse of(ArticleDetail detail) {
            return of(detail.article(), detail.latest(), detail.revisions().size(), detail.revisions());
        }

        public static ArticleResponse of(AdminRow row) {
            return of(row.article(), row.latest(), row.revisionCount(), null);
        }
    }

    public record AdminPageResponse(List<ArticleResponse> items, long total, int page, int size) {}

    /** Public article: only the live revision, its author, sources and reviewer date; never drafts or reviewer ids. */
    public record PublicArticleResponse(UUID id, String slug, ArticleCategory category, String categoryLabel,
                                        ArticleStatus status, String path, Instant publishedAt, Instant firstPublishedAt,
                                        Instant updatedAt, PublicRevision currentRevision) {
        public static PublicArticleResponse of(PublicArticle p) {
            Article a = p.article();
            ArticleRevision r = p.revision();
            return new PublicArticleResponse(a.id(), a.slug(), a.category(), CmsDtos.categoryLabel(a.category()),
                    ArticleStatus.PUBLISHED, "/tin-tuc/" + a.slug(), p.publishedAt(), a.firstPublishedAt(),
                    later(p.publishedAt(), r.reviewedAt()), PublicRevision.of(r));
        }

        /** A revision shown through a preview link (not public: status is the revision's own). */
        public static PublicArticleResponse preview(Article a, ArticleRevision r) {
            return new PublicArticleResponse(a.id(), a.slug(), a.category(), CmsDtos.categoryLabel(a.category()), r.status(),
                    "/tin-tuc/" + a.slug(), null, a.firstPublishedAt(), r.createdAt(), PublicRevision.of(r));
        }
    }

    public record PublicRevision(UUID id, int revisionNumber, String title, String summary, String contentHtml,
                                 String coverImageUrl, String authorName, String legalReference, String metaDescription,
                                 String sourceName, String sourceUrl, Instant reviewedAt) {
        static PublicRevision of(ArticleRevision r) {
            return new PublicRevision(r.id(), r.revisionNumber(), r.title(), r.summary(), r.contentHtml(), r.coverImageUrl(),
                    r.authorName(), r.legalReference(), r.metaDescription(), r.sourceName(), r.sourceUrl(), r.reviewedAt());
        }
    }

    public record PublicPageResponse(List<PublicArticleResponse> items, long total, int page, int size, boolean hasNext) {}

    public static String categoryLabel(ArticleCategory category) {
        return switch (category) {
            case LEGAL_POLICY -> "Chính sách & pháp lý";
            case KNOWLEDGE -> "Kiến thức";
            case MARKET_INSIGHTS -> "Thị trường";
        };
    }

    private static Instant later(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
