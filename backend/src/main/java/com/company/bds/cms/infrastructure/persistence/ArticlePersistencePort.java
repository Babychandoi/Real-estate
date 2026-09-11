package com.company.bds.cms.infrastructure.persistence;

import com.company.bds.cms.domain.model.Article;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleRevision;
import com.company.bds.cms.domain.model.ArticleStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticlePersistencePort {
    Article saveArticle(Article article);
    Optional<Article> findArticleById(UUID id);
    Optional<Article> findArticleBySlug(String slug);
    List<Article> findAllArticles();
    List<Article> findArticlesByCategory(ArticleCategory category);
    List<Article> findArticlesByStatus(ArticleStatus status);

    ArticleRevision saveRevision(ArticleRevision revision);
    Optional<ArticleRevision> findRevisionById(UUID revisionId);
    List<ArticleRevision> findRevisionsByArticleId(UUID articleId);
    List<ArticleRevision> findRevisionsByStatus(ArticleStatus status);
}
