package com.company.bds.cms.infrastructure;

import com.company.bds.cms.application.port.ArticleStore;
import com.company.bds.cms.domain.model.Article;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleRevision;
import com.company.bds.cms.domain.model.ArticleStatus;
import com.company.bds.cms.domain.model.RevisionContent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CMS persistence on plain JDBC. The article that is live at an instant is decided in SQL: a scheduled revision whose
 * publish time has passed is public even before the scheduler promoted it, so scheduled publishing is exact to the
 * request, not to the scheduler's period.
 */
@Repository
public class JdbcArticleStore implements ArticleStore {
    private static final String ARTICLE_COLUMNS = """
            a.id, a.slug, a.category, a.status, a.published_revision_id, a.scheduled_revision_id, a.scheduled_publish_at,
            a.published_at, a.first_published_at, a.unpublished_at, a.created_at, a.updated_at, a.version""";
    private static final String REVISION_COLUMNS = """
            r.id AS r_id, r.article_id AS r_article_id, r.revision_number AS r_number, r.title AS r_title,
            r.summary AS r_summary, r.content_html AS r_content, r.cover_image_url AS r_cover, r.author_name AS r_author,
            r.legal_reference AS r_legal, r.meta_description AS r_meta, r.canonical_url AS r_canonical,
            r.source_name AS r_source_name, r.source_url AS r_source_url, r.status AS r_status,
            r.rejection_reason AS r_rejection, r.created_at AS r_created, r.created_by AS r_created_by,
            r.submitted_at AS r_submitted, r.reviewed_at AS r_reviewed, r.reviewed_by AS r_reviewed_by""";
    /** Live revision / publish time at instant ? (bound twice). */
    private static final String LIVE_REVISION =
            "CASE WHEN a.scheduled_revision_id IS NOT NULL AND a.scheduled_publish_at <= ? THEN a.scheduled_revision_id ELSE a.published_revision_id END";
    private static final String LIVE_AT =
            "CASE WHEN a.scheduled_revision_id IS NOT NULL AND a.scheduled_publish_at <= ? THEN a.scheduled_publish_at ELSE a.published_at END";
    private static final String IS_LIVE =
            "(a.status = 'PUBLISHED' OR (a.status <> 'ARCHIVED' AND a.scheduled_revision_id IS NOT NULL AND a.scheduled_publish_at <= ?))";

    private final JdbcTemplate jdbc;

    public JdbcArticleStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean slugExists(String slug) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM cms_articles WHERE slug = ?)", Boolean.class, slug));
    }

    @Override
    public void insertArticle(UUID id, String slug, ArticleCategory category, Instant now) {
        jdbc.update("INSERT INTO cms_articles(id, slug, category, status, created_at, updated_at) VALUES (?,?,?,'DRAFT',?,?)",
                id, slug, category.name(), ts(now), ts(now));
    }

    @Override
    public void insertRevision(UUID id, UUID articleId, int number, RevisionContent c, UUID createdBy, Instant now) {
        jdbc.update("""
                INSERT INTO cms_article_revisions(id, article_id, revision_number, title, summary, content_html, cover_image_url,
                    author_name, legal_reference, meta_description, canonical_url, source_name, source_url, status,
                    created_at, created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?)""",
                id, articleId, number, c.title(), c.summary(), c.contentHtml(), c.coverImageUrl(), c.authorName(),
                c.legalReference(), c.metaDescription(), c.canonicalUrl(), c.sourceName(), c.sourceUrl(), ts(now), createdBy);
    }

    @Override
    public void updateDraft(UUID revisionId, RevisionContent c) {
        jdbc.update("""
                UPDATE cms_article_revisions SET title = ?, summary = ?, content_html = ?, cover_image_url = ?, author_name = ?,
                    legal_reference = ?, meta_description = ?, canonical_url = ?, source_name = ?, source_url = ?
                WHERE id = ? AND status = 'DRAFT'""",
                c.title(), c.summary(), c.contentHtml(), c.coverImageUrl(), c.authorName(), c.legalReference(),
                c.metaDescription(), c.canonicalUrl(), c.sourceName(), c.sourceUrl(), revisionId);
    }

    @Override
    public Optional<Article> findArticle(UUID id) {
        return jdbc.query("SELECT " + ARTICLE_COLUMNS + " FROM cms_articles a WHERE a.id = ?", ARTICLE, id).stream().findFirst();
    }

    @Override
    public Optional<Article> lockArticle(UUID id) {
        return jdbc.query("SELECT " + ARTICLE_COLUMNS + " FROM cms_articles a WHERE a.id = ? FOR UPDATE", ARTICLE, id).stream().findFirst();
    }

    @Override
    public Optional<Article> findBySlug(String slug) {
        return jdbc.query("SELECT " + ARTICLE_COLUMNS + " FROM cms_articles a WHERE a.slug = ?", ARTICLE, slug).stream().findFirst();
    }

    @Override
    public Optional<ArticleRevision> findRevision(UUID id) {
        return jdbc.query("SELECT " + REVISION_COLUMNS + " FROM cms_article_revisions r WHERE r.id = ?", REVISION, id).stream().findFirst();
    }

    @Override
    public List<ArticleRevision> revisions(UUID articleId, int limit) {
        return jdbc.query("SELECT " + REVISION_COLUMNS + " FROM cms_article_revisions r WHERE r.article_id = ? ORDER BY r.revision_number DESC LIMIT ?",
                REVISION, articleId, limit);
    }

    @Override
    public int nextRevisionNumber(UUID articleId) {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(revision_number), 0) FROM cms_article_revisions WHERE article_id = ?",
                Integer.class, articleId);
        return (max == null ? 0 : max) + 1;
    }

    @Override
    public void setRevisionStatus(UUID revisionId, ArticleStatus status, String rejectionReason, Instant submittedAt,
                                  Instant reviewedAt, String reviewedBy) {
        jdbc.update("""
                UPDATE cms_article_revisions SET status = ?, rejection_reason = ?,
                    submitted_at = COALESCE(?, submitted_at), reviewed_at = COALESCE(?, reviewed_at), reviewed_by = COALESCE(?, reviewed_by)
                WHERE id = ?""",
                status.name(), rejectionReason, ts(submittedAt), ts(reviewedAt), reviewedBy, revisionId);
    }

    @Override
    public void updateArticle(UUID id, ArticleStatus status, UUID publishedRevisionId, UUID scheduledRevisionId,
                              Instant scheduledPublishAt, Instant publishedAt, Instant firstPublishedAt, Instant unpublishedAt,
                              Instant now) {
        jdbc.update("""
                UPDATE cms_articles SET status = ?, published_revision_id = ?, scheduled_revision_id = ?, scheduled_publish_at = ?,
                    published_at = ?, first_published_at = ?, unpublished_at = ?, updated_at = ?, version = version + 1
                WHERE id = ?""",
                status.name(), publishedRevisionId, scheduledRevisionId, ts(scheduledPublishAt), ts(publishedAt),
                ts(firstPublishedAt), ts(unpublishedAt), ts(now), id);
    }

    @Override
    public List<AdminRow> adminPage(ArticleCategory category, ArticleStatus status, int page, int size) {
        Filter filter = adminFilter(category, status);
        List<Object> params = new ArrayList<>(filter.params);
        params.add(size);
        params.add((long) page * size);
        // one statement: the latest revision and the revision count per article (no N+1)
        return jdbc.query("SELECT " + ARTICLE_COLUMNS + ", " + REVISION_COLUMNS + ", rc.revision_count"
                        + " FROM cms_articles a"
                        + " JOIN LATERAL (SELECT * FROM cms_article_revisions x WHERE x.article_id = a.id ORDER BY x.revision_number DESC LIMIT 1) r ON TRUE"
                        + " JOIN LATERAL (SELECT COUNT(*) AS revision_count FROM cms_article_revisions y WHERE y.article_id = a.id) rc ON TRUE"
                        + filter.where + " ORDER BY a.updated_at DESC, a.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new AdminRow(ARTICLE.mapRow(rs, n), REVISION.mapRow(rs, n), rs.getInt("revision_count")),
                params.toArray());
    }

    @Override
    public long adminCount(ArticleCategory category, ArticleStatus status) {
        Filter filter = adminFilter(category, status);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM cms_articles a" + filter.where, Long.class, filter.params.toArray());
        return count == null ? 0 : count;
    }

    @Override
    public List<UUID> dueArticles(Instant now, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM cms_articles WHERE scheduled_revision_id IS NOT NULL AND scheduled_publish_at <= ?
                ORDER BY scheduled_publish_at LIMIT ?""", UUID.class, ts(now), limit);
    }

    @Override
    public Optional<PublicArticle> publicBySlug(String slug, Instant now) {
        Timestamp at = ts(now);
        return jdbc.query("SELECT " + ARTICLE_COLUMNS + ", " + REVISION_COLUMNS + ", " + LIVE_AT + " AS live_at"
                        + " FROM cms_articles a JOIN cms_article_revisions r ON r.id = " + LIVE_REVISION
                        + " WHERE a.slug = ? AND " + IS_LIVE,
                PUBLIC, at, at, slug, at).stream().findFirst();
    }

    @Override
    public PublicPage publicPage(ArticleCategory category, Instant now, int page, int size) {
        Timestamp at = ts(now);
        String categoryClause = category == null ? "" : " AND a.category = ?";
        List<Object> params = new ArrayList<>(List.of(at, at, at));
        if (category != null) params.add(category.name());
        List<Object> countParams = new ArrayList<>(params.subList(2, params.size()));
        params.add(size);
        params.add((long) page * size);
        List<PublicArticle> items = jdbc.query("SELECT " + ARTICLE_COLUMNS + ", " + REVISION_COLUMNS + ", " + LIVE_AT + " AS live_at"
                        + " FROM cms_articles a JOIN cms_article_revisions r ON r.id = " + LIVE_REVISION
                        + " WHERE " + IS_LIVE + categoryClause + " ORDER BY live_at DESC, a.id DESC LIMIT ? OFFSET ?",
                PUBLIC, params.toArray());
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM cms_articles a WHERE " + IS_LIVE + categoryClause, Long.class,
                countParams.toArray());
        return new PublicPage(items, total == null ? 0 : total);
    }

    @Override
    public List<SitemapEntry> sitemapEntries(Instant now, int limit) {
        Timestamp at = ts(now);
        return jdbc.query("SELECT a.slug, GREATEST(a.updated_at, " + LIVE_AT + ") FROM cms_articles a WHERE " + IS_LIVE
                        + " ORDER BY a.id LIMIT ?",
                (rs, n) -> new SitemapEntry(rs.getString(1), instant(rs, 2)), at, at, limit);
    }

    @Override
    public void insertPreviewToken(UUID id, String tokenHash, UUID revisionId, UUID createdBy, Instant expiresAt) {
        jdbc.update("INSERT INTO cms_preview_tokens(id, token_hash, revision_id, created_by, expires_at) VALUES (?,?,?,?,?)",
                id, tokenHash, revisionId, createdBy, ts(expiresAt));
    }

    @Override
    public Optional<UUID> previewRevision(String tokenHash, Instant now) {
        return jdbc.queryForList("SELECT revision_id FROM cms_preview_tokens WHERE token_hash = ? AND expires_at > ?",
                UUID.class, tokenHash, ts(now)).stream().findFirst();
    }

    @Override
    public int purgeExpiredPreviewTokens(Instant now) {
        return jdbc.update("DELETE FROM cms_preview_tokens WHERE expires_at <= ?", ts(now));
    }

    private record Filter(String where, List<Object> params) {}

    private static Filter adminFilter(ArticleCategory category, ArticleStatus status) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (category != null) { clauses.add("a.category = ?"); params.add(category.name()); }
        if (status != null) { clauses.add("a.status = ?"); params.add(status.name()); }
        return new Filter(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), params);
    }

    private static final RowMapper<Article> ARTICLE = (rs, n) -> new Article(
            rs.getObject("id", UUID.class), rs.getString("slug"), ArticleCategory.valueOf(rs.getString("category")),
            ArticleStatus.valueOf(rs.getString("status")), rs.getObject("published_revision_id", UUID.class),
            rs.getObject("scheduled_revision_id", UUID.class), instant(rs, "scheduled_publish_at"),
            instant(rs, "published_at"), instant(rs, "first_published_at"), instant(rs, "unpublished_at"),
            instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));

    private static final RowMapper<ArticleRevision> REVISION = (rs, n) -> new ArticleRevision(
            rs.getObject("r_id", UUID.class), rs.getObject("r_article_id", UUID.class), rs.getInt("r_number"),
            rs.getString("r_title"), rs.getString("r_summary"), rs.getString("r_content"), rs.getString("r_cover"),
            rs.getString("r_author"), rs.getString("r_legal"), rs.getString("r_meta"), rs.getString("r_canonical"),
            rs.getString("r_source_name"), rs.getString("r_source_url"), ArticleStatus.valueOf(rs.getString("r_status")),
            rs.getString("r_rejection"), instant(rs, "r_created"), rs.getObject("r_created_by", UUID.class),
            instant(rs, "r_submitted"), instant(rs, "r_reviewed"), rs.getString("r_reviewed_by"));

    private static final RowMapper<PublicArticle> PUBLIC = (rs, n) ->
            new PublicArticle(ARTICLE.mapRow(rs, n), REVISION.mapRow(rs, n), instant(rs, "live_at"));

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
