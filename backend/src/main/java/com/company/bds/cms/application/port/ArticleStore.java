package com.company.bds.cms.application.port;

import com.company.bds.cms.domain.model.Article;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleRevision;
import com.company.bds.cms.domain.model.ArticleStatus;
import com.company.bds.cms.domain.model.RevisionContent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of CMS articles and revisions (JDBC; public queries read only what a page shows). */
public interface ArticleStore {

    /** Article row + its latest revision, for admin lists. */
    record AdminRow(Article article, ArticleRevision latest, int revisionCount) {}

    /** Public article with the revision that is live at the given instant. */
    record PublicArticle(Article article, ArticleRevision revision, Instant publishedAt) {}

    record PublicPage(List<PublicArticle> items, long total) {}

    /** Slug and lastmod of a public article (sitemap). */
    record SitemapEntry(String slug, Instant lastModified) {}

    boolean slugExists(String slug);

    void insertArticle(UUID id, String slug, ArticleCategory category, Instant now);

    void insertRevision(UUID id, UUID articleId, int number, RevisionContent content, UUID createdBy, Instant now);

    void updateDraft(UUID revisionId, RevisionContent content);

    Optional<Article> findArticle(UUID id);

    /** Locks the article row for the rest of the transaction (workflow transitions are serialised per article). */
    Optional<Article> lockArticle(UUID id);

    Optional<ArticleRevision> findRevision(UUID id);

    List<ArticleRevision> revisions(UUID articleId, int limit);

    int nextRevisionNumber(UUID articleId);

    void setRevisionStatus(UUID revisionId, ArticleStatus status, String rejectionReason, Instant submittedAt,
                           Instant reviewedAt, String reviewedBy);

    /** Article pointers/state after a transition; bumps {@code version} and {@code updated_at}. */
    void updateArticle(UUID id, ArticleStatus status, UUID publishedRevisionId, UUID scheduledRevisionId,
                       Instant scheduledPublishAt, Instant publishedAt, Instant firstPublishedAt, Instant unpublishedAt,
                       Instant now);

    List<AdminRow> adminPage(ArticleCategory category, ArticleStatus status, int page, int size);

    long adminCount(ArticleCategory category, ArticleStatus status);

    /** Ids of articles whose scheduled revision is due at {@code now}. */
    List<UUID> dueArticles(Instant now, int limit);

    Optional<PublicArticle> publicBySlug(String slug, Instant now);

    PublicPage publicPage(ArticleCategory category, Instant now, int page, int size);

    List<SitemapEntry> sitemapEntries(Instant now, int limit);

    Optional<Article> findBySlug(String slug);

    void insertPreviewToken(UUID id, String tokenHash, UUID revisionId, UUID createdBy, Instant expiresAt);

    Optional<UUID> previewRevision(String tokenHash, Instant now);

    int purgeExpiredPreviewTokens(Instant now);
}
