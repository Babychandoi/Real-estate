package com.company.bds.cms.api;

import com.company.bds.cms.api.CmsDtos.ApproveRequest;
import com.company.bds.cms.api.CmsDtos.ArticleResponse;
import com.company.bds.cms.api.CmsDtos.PreviewLinkResponse;
import com.company.bds.cms.api.CmsDtos.RevisionRequest;
import com.company.bds.cms.api.CmsDtos.RevisionResponse;
import com.company.bds.cms.api.request.CreateArticleRequest;
import com.company.bds.cms.api.request.RejectRevisionRequest;
import com.company.bds.cms.application.CmsArticleApplicationService;
import com.company.bds.cms.application.CmsArticleApplicationService.AdminPage;
import com.company.bds.cms.application.CmsArticleApplicationService.Draft;
import com.company.bds.cms.application.CmsArticleApplicationService.PreviewLink;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleStatus;
import com.company.bds.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Staff CMS API (ADMIN/MODERATOR, SecurityConfig {@code /api/v1/cms/**}; responses are no-store). */
@RestController
@RequestMapping("/api/v1/cms/articles")
public class CmsArticleController {
    private final CmsArticleApplicationService service;

    public CmsArticleController(CmsArticleApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ArticleResponse> create(@Valid @RequestBody CreateArticleRequest request, Authentication auth) {
        var detail = service.create(new Draft(request.getSlug(), request.getCategory(), request.toContent()), CurrentUser.id(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ArticleResponse.of(detail));
    }

    /** Newest change first; the array body is kept for v1 clients, the total is in {@code X-Total-Count}. */
    @GetMapping
    public ResponseEntity<List<ArticleResponse>> list(@RequestParam(required = false) ArticleCategory category,
                                                      @RequestParam(required = false) ArticleStatus status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        AdminPage result = service.adminPage(category, status, page, size);
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.total()))
                .body(result.items().stream().map(ArticleResponse::of).toList());
    }

    @GetMapping("/{id}")
    public ArticleResponse detail(@PathVariable UUID id) {
        return ArticleResponse.of(service.detail(id));
    }

    /** Edit: a new DRAFT revision (published and submitted revisions are immutable). */
    @PostMapping("/{id}/revisions")
    public ResponseEntity<ArticleResponse> newRevision(@PathVariable UUID id, @RequestBody RevisionRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ArticleResponse.of(service.newRevision(id, request.toContent(), CurrentUser.id(auth))));
    }

    @PutMapping("/{id}/revisions/{revisionId}")
    public ArticleResponse updateDraft(@PathVariable UUID id, @PathVariable UUID revisionId, @RequestBody RevisionRequest request) {
        return ArticleResponse.of(service.updateDraft(id, revisionId, request.toContent()));
    }

    @PostMapping("/{id}/revisions/{revisionId}/submit")
    public RevisionResponse submit(@PathVariable UUID id, @PathVariable UUID revisionId) {
        return RevisionResponse.of(service.submit(id, revisionId));
    }

    @PostMapping("/{id}/revisions/{revisionId}/approve")
    public RevisionResponse approve(@PathVariable UUID id, @PathVariable UUID revisionId,
                                    @RequestBody(required = false) ApproveRequest request, Authentication auth) {
        return RevisionResponse.of(service.approve(id, revisionId, CurrentUser.id(auth), request == null ? null : request.publishAt()));
    }

    @PostMapping("/{id}/revisions/{revisionId}/reject")
    public RevisionResponse reject(@PathVariable UUID id, @PathVariable UUID revisionId,
                                   @Valid @RequestBody RejectRevisionRequest request, Authentication auth) {
        return RevisionResponse.of(service.reject(id, revisionId, request.getReason(), CurrentUser.id(auth)));
    }

    @DeleteMapping("/{id}/schedule")
    public ArticleResponse cancelSchedule(@PathVariable UUID id) {
        return ArticleResponse.of(service.cancelSchedule(id));
    }

    @PostMapping("/{id}/unpublish")
    public ArticleResponse unpublish(@PathVariable UUID id) {
        return ArticleResponse.of(service.unpublish(id));
    }

    /** Preview link of any revision (24 h). The token is shown once; only its hash is stored. */
    @PostMapping("/{id}/revisions/{revisionId}/preview-link")
    public ResponseEntity<PreviewLinkResponse> previewLink(@PathVariable UUID id, @PathVariable UUID revisionId, Authentication auth) {
        PreviewLink link = service.mintPreview(id, revisionId, CurrentUser.id(auth));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PreviewLinkResponse(link.token(), "/tin-tuc/xem-truoc/" + link.token(), link.expiresAt()));
    }
}
