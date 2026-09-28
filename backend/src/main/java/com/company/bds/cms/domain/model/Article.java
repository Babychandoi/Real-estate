package com.company.bds.cms.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A CMS article: stable slug and category, plus pointers to its public and scheduled revisions (the content lives in
 * immutable {@link ArticleRevision}s, ERD04/ED04). {@code firstPublishedAt} tells an unpublished article (410) from one
 * that was never public (404).
 */
public record Article(UUID id, String slug, ArticleCategory category, ArticleStatus status,
                      UUID publishedRevisionId, UUID scheduledRevisionId, Instant scheduledPublishAt,
                      Instant publishedAt, Instant firstPublishedAt, Instant unpublishedAt,
                      Instant createdAt, Instant updatedAt, long version) {

    public boolean wasEverPublic() { return firstPublishedAt != null; }
}
