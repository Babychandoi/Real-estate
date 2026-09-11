package com.company.bds.cms.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Bản ghi phiên bản bài viết (ContentRevision) tuân thủ tính bất biến ERD04/ED04.
 */
public class ArticleRevision {

    private final UUID id;
    private final UUID articleId;
    private final int revisionNumber;
    private String title;
    private String summary;
    private String contentHtml;
    private String coverImageUrl;
    private String authorName;
    private String legalReference;
    private String metaDescription;
    private String canonicalUrl;
    private ArticleStatus status;
    private String rejectionReason;
    private final Instant createdAt;
    private Instant reviewedAt;
    private String reviewedBy;

    public ArticleRevision(UUID id, UUID articleId, int revisionNumber, String title,
                           String summary, String contentHtml, String coverImageUrl,
                           String authorName, String legalReference, String metaDescription,
                           String canonicalUrl, ArticleStatus status, String rejectionReason,
                           Instant createdAt, Instant reviewedAt, String reviewedBy) {
        this.id = id != null ? id : UUID.randomUUID();
        this.articleId = articleId;
        this.revisionNumber = revisionNumber;
        this.title = title;
        this.summary = summary;
        this.contentHtml = contentHtml;
        this.coverImageUrl = coverImageUrl;
        this.authorName = authorName;
        this.legalReference = legalReference;
        this.metaDescription = metaDescription;
        this.canonicalUrl = canonicalUrl;
        this.status = status != null ? status : ArticleStatus.DRAFT;
        this.rejectionReason = rejectionReason;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
    }

    public static ArticleRevision createInitial(UUID articleId, String title, String summary,
                                               String contentHtml, String coverImageUrl,
                                               String authorName, String legalReference,
                                               String metaDescription, String canonicalUrl) {
        return new ArticleRevision(
                UUID.randomUUID(),
                articleId,
                1,
                title,
                summary,
                contentHtml,
                coverImageUrl,
                authorName,
                legalReference,
                metaDescription,
                canonicalUrl,
                ArticleStatus.DRAFT,
                null,
                Instant.now(),
                null,
                null
        );
    }

    public void submit() {
        this.status = ArticleStatus.SUBMITTED;
    }

    public void approve(String adminUsername) {
        this.status = ArticleStatus.PUBLISHED;
        this.reviewedAt = Instant.now();
        this.reviewedBy = adminUsername;
        this.rejectionReason = null;
    }

    public void reject(String reason, String adminUsername) {
        this.status = ArticleStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedAt = Instant.now();
        this.reviewedBy = adminUsername;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getArticleId() { return articleId; }
    public int getRevisionNumber() { return revisionNumber; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getContentHtml() { return contentHtml; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getAuthorName() { return authorName; }
    public String getLegalReference() { return legalReference; }
    public String getMetaDescription() { return metaDescription; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public ArticleStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
}
