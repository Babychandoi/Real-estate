package com.company.bds.cms.application;

import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Makes scheduled CMS publications durable once due (one instance at a time). Public reads already show a due
 * revision without waiting for this task, so its period only affects the admin state and sitemap lastmod.
 */
@Component
public class CmsPublishScheduler {
    private static final Logger log = LoggerFactory.getLogger(CmsPublishScheduler.class);
    static final String LOCK_NAME = "cms-scheduled-publish";

    private final CmsArticleApplicationService service;
    private final ScheduledTaskLock lock;

    public CmsPublishScheduler(CmsArticleApplicationService service, ScheduledTaskLock lock) {
        this.service = service;
        this.lock = lock;
    }

    @Scheduled(fixedDelayString = "${app.cms.publish-poll:PT1M}", initialDelayString = "${app.cms.publish-initial-delay:PT30S}")
    public void publishDue() {
        lock.runExclusive(LOCK_NAME, Duration.ofMinutes(5), () -> {
            int promoted = service.publishDue(200);
            if (promoted > 0) log.info("cms_scheduled_published count={}", promoted);
        });
    }
}
