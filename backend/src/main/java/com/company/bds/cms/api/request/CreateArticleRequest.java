package com.company.bds.cms.api.request;

import com.company.bds.cms.domain.model.ArticleCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateArticleRequest {

    @NotBlank(message = "Slug bài viết không được để trống (FR26)")
    private String slug;

    @NotNull(message = "Danh mục bài viết không được để trống (FR32)")
    private ArticleCategory category;

    @NotBlank(message = "Tiêu đề bài viết không được để trống")
    private String title;

    private String summary;

    @NotBlank(message = "Nội dung bài viết không được để trống")
    private String contentHtml;

    private String coverImageUrl;

    @NotBlank(message = "Tên biên tập viên tác giả không được để trống")
    private String authorName;

    private String legalReference;
    private String metaDescription;
    private String canonicalUrl;

    public CreateArticleRequest() {}

    // Getters and Setters
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public ArticleCategory getCategory() { return category; }
    public void setCategory(ArticleCategory category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContentHtml() { return contentHtml; }
    public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getLegalReference() { return legalReference; }
    public void setLegalReference(String legalReference) { this.legalReference = legalReference; }
    public String getMetaDescription() { return metaDescription; }
    public void setMetaDescription(String metaDescription) { this.metaDescription = metaDescription; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }
}
