package com.company.bds.cms.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thực thể bài viết gốc quản lý định danh slug, category và các phiên bản revision.
 */
public class Article {

    private final UUID id;
    private String slug;
    private ArticleCategory category;
    private ArticleStatus status;
    private UUID publishedRevisionId;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<ArticleRevision> revisions = new ArrayList<>();

    public Article(UUID id, String slug, ArticleCategory category, ArticleStatus status,
                   UUID publishedRevisionId, Instant createdAt, Instant updatedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.slug = slug;
        this.category = category;
        this.status = status != null ? status : ArticleStatus.DRAFT;
        this.publishedRevisionId = publishedRevisionId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Article createNew(String slug, ArticleCategory category) {
        return new Article(
                UUID.randomUUID(),
                slug,
                category,
                ArticleStatus.DRAFT,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    public void addRevision(ArticleRevision revision) {
        this.revisions.add(revision);
        this.updatedAt = Instant.now();
    }

    public void publishRevision(UUID revisionId) {
        this.publishedRevisionId = revisionId;
        this.status = ArticleStatus.PUBLISHED;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = ArticleStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public ArticleCategory getCategory() { return category; }
    public ArticleStatus getStatus() { return status; }
    public UUID getPublishedRevisionId() { return publishedRevisionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ArticleRevision> getRevisions() { return revisions; }
}
