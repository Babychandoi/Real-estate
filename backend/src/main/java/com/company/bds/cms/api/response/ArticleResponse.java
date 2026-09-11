package com.company.bds.cms.api.response;

import com.company.bds.cms.domain.model.Article;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleRevision;
import com.company.bds.cms.domain.model.ArticleStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ArticleResponse {
    private UUID id;
    private String slug;
    private ArticleCategory category;
    private ArticleStatus status;
    private UUID publishedRevisionId;
    private Instant createdAt;
    private Instant updatedAt;
    private ArticleRevisionResponse currentRevision;
    private List<ArticleRevisionResponse> revisions;

    public ArticleResponse() {}

    public static ArticleResponse fromDomain(Article domain, ArticleRevision latestRevision, List<ArticleRevision> allRevisions) {
        ArticleResponse res = new ArticleResponse();
        res.id = domain.getId();
        res.slug = domain.getSlug();
        res.category = domain.getCategory();
        res.status = domain.getStatus();
        res.publishedRevisionId = domain.getPublishedRevisionId();
        res.createdAt = domain.getCreatedAt();
        res.updatedAt = domain.getUpdatedAt();

        if (latestRevision != null) {
            res.currentRevision = ArticleRevisionResponse.fromDomain(latestRevision);
        }
        if (allRevisions != null) {
            res.revisions = allRevisions.stream().map(ArticleRevisionResponse::fromDomain).collect(Collectors.toList());
        }
        return res;
    }

    // Getters
    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public ArticleCategory getCategory() { return category; }
    public ArticleStatus getStatus() { return status; }
    public UUID getPublishedRevisionId() { return publishedRevisionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public ArticleRevisionResponse getCurrentRevision() { return currentRevision; }
    public List<ArticleRevisionResponse> getRevisions() { return revisions; }
}
