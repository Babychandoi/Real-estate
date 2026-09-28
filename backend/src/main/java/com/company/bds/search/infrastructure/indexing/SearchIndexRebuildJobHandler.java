package com.company.bds.search.infrastructure.indexing;

import com.company.bds.search.infrastructure.SearchIndexStateRepository;
import com.company.bds.shared.jobs.ClaimedJob;
import com.company.bds.shared.jobs.JobBatchResult;
import com.company.bds.shared.jobs.JobHandler;
import com.company.bds.shared.jobs.JobQueue;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Queue {@code search-rebuild}: backfills a BUILDING index in slices of {@value #BATCHES_PER_RUN} batches (so a run
 * stays far inside its lease), re-enqueues itself until the read model is covered, then swaps the alias.
 */
@Component
public class SearchIndexRebuildJobHandler implements JobHandler {
    public static final String QUEUE = "search-rebuild";
    static final int BATCHES_PER_RUN = 40;

    private final SearchIndexLifecycle lifecycle;
    private final SearchIndexStateRepository states;
    private final JobQueue jobs;

    public SearchIndexRebuildJobHandler(SearchIndexLifecycle lifecycle, SearchIndexStateRepository states, JobQueue jobs) {
        this.lifecycle = lifecycle;
        this.states = states;
        this.jobs = jobs;
    }

    @Override public String queue() { return QUEUE; }

    @Override public int batchSize() { return 1; }

    @Override public Duration lease() { return Duration.ofMinutes(5); }

    @Override
    public JobBatchResult handle(List<ClaimedJob> claimed) {
        JobBatchResult.Builder result = JobBatchResult.builder();
        for (ClaimedJob job : claimed) {
            String index = job.text("index");
            var state = index == null ? null : states.find(index).orElse(null);
            if (state == null || state.role() != SearchIndexStateRepository.Role.BUILDING) {
                result.succeed(job); // cancelled or already activated
                continue;
            }
            if (lifecycle.backfillStep(index, BATCHES_PER_RUN)) {
                lifecycle.activate(index);
            } else {
                jobs.enqueue(QUEUE, index + ":continue", Map.of("index", index), null);
            }
            result.succeed(job);
        }
        return result.build();
    }
}
