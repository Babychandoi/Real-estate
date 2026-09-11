package com.company.bds.cms.api.response;

import com.company.bds.cms.domain.model.ArticleRevision;
import com.company.bds.cms.domain.model.ArticleStatus;

import java.time.Instant;
import java.util.UUID;

public class ArticleRevisionResponse {
    private UUID id;
    private UUID articleId;
    private int revisionNumber;
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
    private Instant createdAt;
    private Instant reviewedAt;
    private String reviewedBy;

    public ArticleRevisionResponse() {}

    public static ArticleRevisionResponse fromDomain(ArticleRevision domain) {
        ArticleRevisionResponse res = new ArticleRevisionResponse();
        res.id = domain.getId();
        res.articleId = domain.getArticleId();
        res.revisionNumber = domain.getRevisionNumber();
        res.title = domain.getTitle();
        res.summary = domain.getSummary();
        res.contentHtml = domain.getContentHtml();
        res.coverImageUrl = domain.getCoverImageUrl();
        res.authorName = domain.getAuthorName();
        res.legalReference = domain.getLegalReference();
        res.metaDescription = domain.getMetaDescription();
        res.canonicalUrl = domain.getCanonicalUrl();
        res.status = domain.getStatus();
        res.rejectionReason = domain.getRejectionReason();
        res.createdAt = domain.getCreatedAt();
        res.reviewedAt = domain.getReviewedAt();
        res.reviewedBy = domain.getReviewedBy();
        return res;
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
