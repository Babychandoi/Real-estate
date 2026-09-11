package com.company.bds.cms.application;

import com.company.bds.cms.api.request.CreateArticleRequest;
import com.company.bds.cms.domain.model.Article;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleRevision;
import com.company.bds.cms.domain.model.ArticleStatus;
import com.company.bds.cms.infrastructure.persistence.ArticlePersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CmsArticleApplicationService {

    private final ArticlePersistencePort persistencePort;

    public CmsArticleApplicationService(ArticlePersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    public Article createArticle(CreateArticleRequest request) {
        // 1. Tạo thực thể Article gốc
        Article article = Article.createNew(request.getSlug(), request.getCategory());
        Article savedArticle = persistencePort.saveArticle(article);

        // 2. Tạo bản ghi ContentRevision ban đầu (Revision 1, DRAFT)
        ArticleRevision revision = ArticleRevision.createInitial(
                savedArticle.getId(),
                request.getTitle(),
                request.getSummary(),
                request.getContentHtml(),
                request.getCoverImageUrl(),
                request.getAuthorName(),
                request.getLegalReference(),
                request.getMetaDescription(),
                request.getCanonicalUrl()
        );
        persistencePort.saveRevision(revision);

        return savedArticle;
    }

    public ArticleRevision submitRevision(UUID articleId, UUID revisionId) {
        ArticleRevision revision = persistencePort.findRevisionById(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));

        if (!revision.getArticleId().equals(articleId)) {
            throw new IllegalArgumentException("Revision does not belong to article: " + articleId);
        }

        revision.submit();
        ArticleRevision savedRevision = persistencePort.saveRevision(revision);

        // Cập nhật trạng thái bài viết sang SUBMITTED nếu bài chưa được publish
        Article article = persistencePort.findArticleById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + articleId));

        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            Article updatedArticle = new Article(
                    article.getId(),
                    article.getSlug(),
                    article.getCategory(),
                    ArticleStatus.SUBMITTED,
                    article.getPublishedRevisionId(),
                    article.getCreatedAt(),
                    java.time.Instant.now()
            );
            persistencePort.saveArticle(updatedArticle);
        }

        return savedRevision;
    }

    public ArticleRevision approveRevision(UUID articleId, UUID revisionId, String adminUsername) {
        ArticleRevision revision = persistencePort.findRevisionById(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));

        revision.approve(adminUsername != null ? adminUsername : "admin");
        ArticleRevision savedRevision = persistencePort.saveRevision(revision);

        // Xuất bản bài viết
        Article article = persistencePort.findArticleById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + articleId));

        article.publishRevision(revisionId);
        persistencePort.saveArticle(article);

        return savedRevision;
    }

    public ArticleRevision rejectRevision(UUID articleId, UUID revisionId, String reason, String adminUsername) {
        ArticleRevision revision = persistencePort.findRevisionById(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));

        revision.reject(reason, adminUsername != null ? adminUsername : "admin");
        return persistencePort.saveRevision(revision);
    }

    @Transactional(readOnly = true)
    public List<Article> getAllArticles() {
        return persistencePort.findAllArticles();
    }

    @Transactional(readOnly = true)
    public List<Article> getArticlesByCategory(ArticleCategory category) {
        return persistencePort.findArticlesByCategory(category);
    }

    @Transactional(readOnly = true)
    public Optional<Article> getArticleById(UUID id) {
        return persistencePort.findArticleById(id);
    }

    @Transactional(readOnly = true)
    public List<ArticleRevision> getRevisions(UUID articleId) {
        return persistencePort.findRevisionsByArticleId(articleId);
    }

    @Transactional(readOnly = true)
    public Optional<ArticleRevision> getLatestRevision(UUID articleId) {
        List<ArticleRevision> list = persistencePort.findRevisionsByArticleId(articleId);
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(list.get(0));
    }

    @Transactional(readOnly = true)
    public List<Article> getPublicArticles() {
        return persistencePort.findArticlesByStatus(ArticleStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public Optional<Article> getPublicArticleBySlug(String slug) {
        return persistencePort.findArticleBySlug(slug)
                .filter(a -> a.getStatus() == ArticleStatus.PUBLISHED);
    }
}
