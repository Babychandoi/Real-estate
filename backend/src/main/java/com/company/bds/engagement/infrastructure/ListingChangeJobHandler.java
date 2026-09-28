package com.company.bds.engagement.infrastructure;

import com.company.bds.engagement.application.AlertMatchingService;
import com.company.bds.shared.jobs.ClaimedJob;
import com.company.bds.shared.jobs.JobBatchResult;
import com.company.bds.shared.jobs.JobHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Queue {@value #QUEUE}: one coalesced job per listing whose public row appeared, disappeared or changed price (V070
 * trigger). Each job runs in its own transaction, so one failing listing does not hold back the others.
 */
@Component
public class ListingChangeJobHandler implements JobHandler {
    public static final String QUEUE = "engage-listing-change";

    private final AlertMatchingService matching;

    public ListingChangeJobHandler(AlertMatchingService matching) {
        this.matching = matching;
    }

    @Override
    public String queue() { return QUEUE; }

    @Override
    public int batchSize() { return 50; }

    @Override
    public int maxAttempts() { return 20; }

    @Override
    public JobBatchResult handle(List<ClaimedJob> jobs) {
        JobBatchResult.Builder result = JobBatchResult.builder();
        for (ClaimedJob job : jobs) {
            String id = job.text("listingId");
            UUID listingId;
            try {
                listingId = UUID.fromString(id);
            } catch (RuntimeException ex) {
                result.failPermanently(job, "invalid listingId");
                continue;
            }
            try {
                matching.process(listingId);
                result.succeed(job);
            } catch (RuntimeException ex) {
                result.fail(job, ex.getClass().getSimpleName());
            }
        }
        return result.build();
    }
}
