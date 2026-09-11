package com.company.bds.cms.infrastructure.persistence;

import com.company.bds.cms.domain.model.Article;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleRevision;
import com.company.bds.cms.domain.model.ArticleStatus;
import com.company.bds.cms.infrastructure.persistence.entity.ArticleJpaEntity;
import com.company.bds.cms.infrastructure.persistence.entity.ArticleRevisionJpaEntity;
import com.company.bds.cms.infrastructure.persistence.repository.ArticleJpaRepository;
import com.company.bds.cms.infrastructure.persistence.repository.ArticleRevisionJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ArticlePersistenceAdapter implements ArticlePersistencePort {

    private final ArticleJpaRepository articleRepo;
    private final ArticleRevisionJpaRepository revisionRepo;

    public ArticlePersistenceAdapter(ArticleJpaRepository articleRepo, ArticleRevisionJpaRepository revisionRepo) {
        this.articleRepo = articleRepo;
        this.revisionRepo = revisionRepo;
    }

    @Override
    public Article saveArticle(Article article) {
        ArticleJpaEntity entity = articleRepo.findById(article.getId())
                .orElseGet(() -> new ArticleJpaEntity(
                        article.getId(),
                        article.getSlug(),
                        article.getCategory(),
                        article.getStatus(),
                        article.getPublishedRevisionId(),
                        article.getCreatedAt(),
                        article.getUpdatedAt()
                ));
        entity.setSlug(article.getSlug());
        entity.setCategory(article.getCategory());
        entity.setStatus(article.getStatus());
        entity.setPublishedRevisionId(article.getPublishedRevisionId());
        entity.setUpdatedAt(article.getUpdatedAt());

        ArticleJpaEntity saved = articleRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Article> findArticleById(UUID id) {
        return articleRepo.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Article> findArticleBySlug(String slug) {
        return articleRepo.findBySlug(slug).map(this::toDomain);
    }

    @Override
    public List<Article> findAllArticles() {
        return articleRepo.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Article> findArticlesByCategory(ArticleCategory category) {
        return articleRepo.findByCategory(category).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Article> findArticlesByStatus(ArticleStatus status) {
        return articleRepo.findByStatus(status).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public ArticleRevision saveRevision(ArticleRevision revision) {
        ArticleJpaEntity articleEntity = articleRepo.findById(revision.getArticleId())
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + revision.getArticleId()));

        ArticleRevisionJpaEntity entity = revisionRepo.findById(revision.getId())
                .orElseGet(() -> {
                    ArticleRevisionJpaEntity e = new ArticleRevisionJpaEntity();
                    e.setId(revision.getId());
                    e.setArticle(articleEntity);
                    return e;
                });

        entity.setRevisionNumber(revision.getRevisionNumber());
        entity.setTitle(revision.getTitle());
        entity.setSummary(revision.getSummary());
        entity.setContentHtml(revision.getContentHtml());
        entity.setCoverImageUrl(revision.getCoverImageUrl());
        entity.setAuthorName(revision.getAuthorName());
        entity.setLegalReference(revision.getLegalReference());
        entity.setMetaDescription(revision.getMetaDescription());
        entity.setCanonicalUrl(revision.getCanonicalUrl());
        entity.setStatus(revision.getStatus());
        entity.setRejectionReason(revision.getRejectionReason());
        entity.setCreatedAt(revision.getCreatedAt());
        entity.setReviewedAt(revision.getReviewedAt());
        entity.setReviewedBy(revision.getReviewedBy());

        ArticleRevisionJpaEntity saved = revisionRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<ArticleRevision> findRevisionById(UUID revisionId) {
        return revisionRepo.findById(revisionId).map(this::toDomain);
    }

    @Override
    public List<ArticleRevision> findRevisionsByArticleId(UUID articleId) {
        return revisionRepo.findByArticleIdOrderByRevisionNumberDesc(articleId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArticleRevision> findRevisionsByStatus(ArticleStatus status) {
        return revisionRepo.findByStatus(status).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private Article toDomain(ArticleJpaEntity entity) {
        return new Article(
                entity.getId(),
                entity.getSlug(),
                entity.getCategory(),
                entity.getStatus(),
                entity.getPublishedRevisionId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ArticleRevision toDomain(ArticleRevisionJpaEntity entity) {
        return new ArticleRevision(
                entity.getId(),
                entity.getArticle().getId(),
                entity.getRevisionNumber(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getContentHtml(),
                entity.getCoverImageUrl(),
                entity.getAuthorName(),
                entity.getLegalReference(),
                entity.getMetaDescription(),
                entity.getCanonicalUrl(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getCreatedAt(),
                entity.getReviewedAt(),
                entity.getReviewedBy()
        );
    }
}
