package com.company.bds.cms.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One revision of an article. Its content never changes once it left DRAFT (a database trigger enforces it); an edit
 * of published content is a new revision. {@code reviewedBy} is the reviewer's user id.
 */
public record ArticleRevision(UUID id, UUID articleId, int revisionNumber, String title, String summary,
                              String contentHtml, String coverImageUrl, String authorName, String legalReference,
                              String metaDescription, String canonicalUrl, String sourceName, String sourceUrl,
                              ArticleStatus status, String rejectionReason, Instant createdAt, UUID createdBy,
                              Instant submittedAt, Instant reviewedAt, String reviewedBy) {
}
