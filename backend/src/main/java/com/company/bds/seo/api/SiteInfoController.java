package com.company.bds.seo.api;

import com.company.bds.cms.application.CmsArticleApplicationService;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.seo.application.SiteOperator;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Operator identity (configuration) and the approved policy articles the information pages link to (UI-15). */
@RestController
public class SiteInfoController {
    private final SiteOperator operator;
    private final CmsArticleApplicationService cms;

    public SiteInfoController(SiteOperator operator, CmsArticleApplicationService cms) {
        this.operator = operator;
        this.cms = cms;
    }

    public record PolicyLink(String title, String path, String summary, Instant publishedAt) {}

    public record SiteInfoResponse(SiteOperator.Info operator, List<PolicyLink> policies) {}

    @GetMapping("/api/v1/public/site-info")
    public ResponseEntity<SiteInfoResponse> siteInfo() {
        List<PolicyLink> policies = cms.publicPage(ArticleCategory.LEGAL_POLICY, 0, 20).items().stream()
                .map(p -> new PolicyLink(p.revision().title(), "/tin-tuc/" + p.article().slug(), p.revision().summary(), p.publishedAt()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new SiteInfoResponse(operator.info(), policies));
    }
}
