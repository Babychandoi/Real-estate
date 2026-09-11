package com.company.bds.cms.infrastructure.persistence.entity;

import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cms_articles")
public class ArticleJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArticleCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArticleStatus status;

    @Column(name = "published_revision_id")
    private UUID publishedRevisionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ArticleRevisionJpaEntity> revisions = new ArrayList<>();

    public ArticleJpaEntity() {}

    public ArticleJpaEntity(UUID id, String slug, ArticleCategory category, ArticleStatus status,
                            UUID publishedRevisionId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.slug = slug;
        this.category = category;
        this.status = status;
        this.publishedRevisionId = publishedRevisionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public ArticleCategory getCategory() { return category; }
    public void setCategory(ArticleCategory category) { this.category = category; }
    public ArticleStatus getStatus() { return status; }
    public void setStatus(ArticleStatus status) { this.status = status; }
    public UUID getPublishedRevisionId() { return publishedRevisionId; }
    public void setPublishedRevisionId(UUID publishedRevisionId) { this.publishedRevisionId = publishedRevisionId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<ArticleRevisionJpaEntity> getRevisions() { return revisions; }
    public void setRevisions(List<ArticleRevisionJpaEntity> revisions) { this.revisions = revisions; }
}
