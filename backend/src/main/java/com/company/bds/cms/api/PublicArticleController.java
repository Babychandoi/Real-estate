package com.company.bds.cms.api;

import com.company.bds.cms.api.CmsDtos.PublicArticleResponse;
import com.company.bds.cms.api.CmsDtos.PublicPageResponse;
import com.company.bds.cms.application.CmsArticleApplicationService;
import com.company.bds.cms.application.CmsArticleApplicationService.PublicLookup;
import com.company.bds.cms.application.port.ArticleStore.PublicPage;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.shared.error.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Public CMS reads: only live revisions (published, or scheduled and due). Unpublished articles answer
 * {@code 410 ARTICLE_GONE}, unknown or never-published ones {@code 404}. Previews are token-bearing and no-store.
 */
@RestController
public class PublicArticleController {
    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final CmsArticleApplicationService service;

    public PublicArticleController(CmsArticleApplicationService service) {
        this.service = service;
    }

    /** v1 array of the newest public articles (kept for existing clients). */
    @GetMapping("/api/v1/public/articles")
    public ResponseEntity<List<PublicArticleResponse>> list(@RequestParam(required = false) ArticleCategory category,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        PublicPage result = service.publicPage(category, page, size);
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE)
                .body(result.items().stream().map(PublicArticleResponse::of).toList());
    }

    @GetMapping("/api/v2/public/articles")
    public ResponseEntity<PublicPageResponse> page(@RequestParam(required = false) ArticleCategory category,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "12") int size) {
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(0, page);
        PublicPage result = service.publicPage(category, safePage, safeSize);
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(new PublicPageResponse(
                result.items().stream().map(PublicArticleResponse::of).toList(), result.total(), safePage, safeSize,
                (long) (safePage + 1) * safeSize < result.total()));
    }

    @GetMapping({"/api/v1/public/articles/{slug}", "/api/v2/public/articles/{slug}"})
    public ResponseEntity<PublicArticleResponse> bySlug(@PathVariable String slug) {
        PublicLookup lookup = service.publicBySlug(slug);
        if (lookup.article() != null) return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(PublicArticleResponse.of(lookup.article()));
        if (lookup.gone()) throw new ApiException(HttpStatus.GONE, "ARTICLE_GONE", "Bài viết đã được gỡ khỏi trang công khai.");
        throw ApiException.notFound("ARTICLE_NOT_FOUND", "Không tìm thấy bài viết.");
    }

    @GetMapping("/api/v1/public/articles/preview/{token}")
    public ResponseEntity<PublicArticleResponse> preview(@PathVariable String token) {
        return service.preview(token)
                .map(detail -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .header("X-Robots-Tag", "noindex, nofollow")
                        .body(PublicArticleResponse.preview(detail.article(), detail.latest())))
                .orElseThrow(() -> ApiException.notFound("PREVIEW_NOT_FOUND", "Liên kết xem trước không hợp lệ hoặc đã hết hạn."));
    }
}
