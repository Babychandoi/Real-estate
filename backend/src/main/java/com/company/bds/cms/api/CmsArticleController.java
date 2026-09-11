package com.company.bds.cms.api;

import com.company.bds.cms.api.request.CreateArticleRequest;
import com.company.bds.cms.api.request.RejectRevisionRequest;
import com.company.bds.cms.api.response.ArticleResponse;
import com.company.bds.cms.api.response.ArticleRevisionResponse;
import com.company.bds.cms.application.CmsArticleApplicationService;
import com.company.bds.cms.domain.model.Article;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleRevision;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class CmsArticleController {

    private final CmsArticleApplicationService articleService;

    public CmsArticleController(CmsArticleApplicationService articleService) {
        this.articleService = articleService;
    }

    // ================== ADMIN CMS ENDPOINTS ==================

    @PostMapping("/api/v1/cms/articles")
    public ResponseEntity<ArticleResponse> createArticle(@Valid @RequestBody CreateArticleRequest request) {
        Article article = articleService.createArticle(request);
        List<ArticleRevision> revisions = articleService.getRevisions(article.getId());
        ArticleRevision latest = revisions.isEmpty() ? null : revisions.get(0);
        return ResponseEntity.status(HttpStatus.CREATED).body(ArticleResponse.fromDomain(article, latest, revisions));
    }

    @GetMapping("/api/v1/cms/articles")
    public ResponseEntity<List<ArticleResponse>> getAdminArticles(@RequestParam(required = false) ArticleCategory category) {
        List<Article> articles = category != null
                ? articleService.getArticlesByCategory(category)
                : articleService.getAllArticles();

        List<ArticleResponse> responseList = articles.stream().map(a -> {
            List<ArticleRevision> revs = articleService.getRevisions(a.getId());
            ArticleRevision latest = revs.isEmpty() ? null : revs.get(0);
            return ArticleResponse.fromDomain(a, latest, revs);
        }).collect(Collectors.toList());

        return ResponseEntity.ok(responseList);
    }

    @GetMapping("/api/v1/cms/articles/{id}")
    public ResponseEntity<ArticleResponse> getArticleDetail(@PathVariable UUID id) {
        return articleService.getArticleById(id)
                .map(a -> {
                    List<ArticleRevision> revs = articleService.getRevisions(a.getId());
                    ArticleRevision latest = revs.isEmpty() ? null : revs.get(0);
                    return ResponseEntity.ok(ArticleResponse.fromDomain(a, latest, revs));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/cms/articles/{articleId}/revisions/{revisionId}/submit")
    public ResponseEntity<ArticleRevisionResponse> submitRevision(
            @PathVariable UUID articleId,
            @PathVariable UUID revisionId) {
        ArticleRevision revision = articleService.submitRevision(articleId, revisionId);
        return ResponseEntity.ok(ArticleRevisionResponse.fromDomain(revision));
    }

    @PostMapping("/api/v1/cms/articles/{articleId}/revisions/{revisionId}/approve")
    public ResponseEntity<ArticleRevisionResponse> approveRevision(
            @PathVariable UUID articleId,
            @PathVariable UUID revisionId,
            @RequestParam(required = false, defaultValue = "admin") String adminUsername) {
        ArticleRevision revision = articleService.approveRevision(articleId, revisionId, adminUsername);
        return ResponseEntity.ok(ArticleRevisionResponse.fromDomain(revision));
    }

    @PostMapping("/api/v1/cms/articles/{articleId}/revisions/{revisionId}/reject")
    public ResponseEntity<ArticleRevisionResponse> rejectRevision(
            @PathVariable UUID articleId,
            @PathVariable UUID revisionId,
            @Valid @RequestBody RejectRevisionRequest request) {
        ArticleRevision revision = articleService.rejectRevision(articleId, revisionId, request.getReason(), request.getAdminUsername());
        return ResponseEntity.ok(ArticleRevisionResponse.fromDomain(revision));
    }

    // ================== PUBLIC ENDPOINTS ==================

    @GetMapping("/api/v1/public/articles")
    public ResponseEntity<List<ArticleResponse>> getPublicArticles() {
        List<Article> articles = articleService.getPublicArticles();
        List<ArticleResponse> responseList = articles.stream().map(a -> {
            List<ArticleRevision> revs = articleService.getRevisions(a.getId());
            ArticleRevision latest = revs.isEmpty() ? null : revs.get(0);
            return ArticleResponse.fromDomain(a, latest, revs);
        }).collect(Collectors.toList());

        return ResponseEntity.ok(responseList);
    }

    @GetMapping("/api/v1/public/articles/{slug}")
    public ResponseEntity<ArticleResponse> getPublicArticleBySlug(@PathVariable String slug) {
        return articleService.getPublicArticleBySlug(slug)
                .map(a -> {
                    List<ArticleRevision> revs = articleService.getRevisions(a.getId());
                    ArticleRevision latest = revs.isEmpty() ? null : revs.get(0);
                    return ResponseEntity.ok(ArticleResponse.fromDomain(a, latest, revs));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
