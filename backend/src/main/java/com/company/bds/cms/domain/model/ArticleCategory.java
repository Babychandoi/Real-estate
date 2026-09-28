package com.company.bds.cms.domain.model;

/**
 * Danh mục bài viết CMS tuân thủ FR32 và UC07.
 */
public enum ArticleCategory {
    LEGAL_POLICY("Chính sách & pháp lý"),    // Chính sách & Pháp lý (FR32)
    KNOWLEDGE("Kiến thức"),                  // Chuyên mục kiến thức BĐS
    MARKET_INSIGHTS("Thị trường");           // Cẩm nang & Báo cáo thị trường

    private final String label;

    ArticleCategory(String label) { this.label = label; }

    /** Vietnamese display label used by the public API and the prerendered pages. */
    public String label() { return label; }
}
