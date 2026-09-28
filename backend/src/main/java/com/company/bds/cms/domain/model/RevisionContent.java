package com.company.bds.cms.domain.model;

/** Editable content of a revision (already validated and sanitised by the application service). */
public record RevisionContent(String title, String summary, String contentHtml, String coverImageUrl, String authorName,
                              String legalReference, String metaDescription, String canonicalUrl, String sourceName,
                              String sourceUrl) {
}
