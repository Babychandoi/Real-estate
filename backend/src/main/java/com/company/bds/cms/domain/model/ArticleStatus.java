package com.company.bds.cms.domain.model;

/**
 * Trạng thái vòng đời bài viết và revision theo chuẩn FR24 / ED04.
 */
public enum ArticleStatus {
    DRAFT,          // Đang soạn thảo bởi BTV
    SUBMITTED,      // Chờ duyệt xuất bản (nộp bởi BTV)
    PUBLISHED,      // Đã phê duyệt và công khai bởi Admin
    ARCHIVED,       // Đã lưu trữ / gỡ bài
    REJECTED        // Trả về yêu cầu sửa đổi
}
