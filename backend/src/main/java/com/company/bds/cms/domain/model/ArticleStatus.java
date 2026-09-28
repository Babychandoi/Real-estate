package com.company.bds.cms.domain.model;

/**
 * Trạng thái vòng đời bài viết và revision theo chuẩn FR24 / ED04.
 */
public enum ArticleStatus {
    DRAFT,          // Đang soạn thảo bởi BTV
    SUBMITTED,      // Chờ duyệt xuất bản (nộp bởi BTV)
    SCHEDULED,      // Revision đã duyệt, chờ tới thời điểm xuất bản (S7)
    PUBLISHED,      // Đã phê duyệt và công khai bởi Admin
    SUPERSEDED,     // Revision từng công khai, đã được thay bằng revision mới hơn (S7)
    ARCHIVED,       // Đã lưu trữ / gỡ bài
    REJECTED        // Trả về yêu cầu sửa đổi
}
