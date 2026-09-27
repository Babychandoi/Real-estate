package com.company.bds.shared.jobs;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Outcome of one {@link JobHandler#handle} call: succeeded ids, retryable failures with a reason, and permanent failures
 * (dead-lettered at once, e.g. an address the mail server refuses with 5xx). Ids in none of them are retried.
 */
public record JobBatchResult(Set<UUID> succeeded, Map<UUID, String> failures, Map<UUID, String> permanentFailures) {

    public JobBatchResult {
        succeeded = Set.copyOf(succeeded);
        failures = Map.copyOf(failures);
        permanentFailures = Map.copyOf(permanentFailures);
    }

    public JobBatchResult(Set<UUID> succeeded, Map<UUID, String> failures) {
        this(succeeded, failures, Map.of());
    }

    public static JobBatchResult allSucceeded(Collection<ClaimedJob> jobs) {
        return new JobBatchResult(jobs.stream().map(ClaimedJob::id).collect(Collectors.toSet()), Map.of());
    }

    public static JobBatchResult succeeded(Collection<UUID> ids) {
        return new JobBatchResult(new HashSet<>(ids), Map.of());
    }

    /** Every job failed (retryable) for the same reason. */
    public static JobBatchResult allFailed(Collection<ClaimedJob> jobs, String reason) {
        Builder builder = builder();
        jobs.forEach(job -> builder.fail(job, reason));
        return builder.build();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Set<UUID> succeeded = new HashSet<>();
        private final Map<UUID, String> failures = new HashMap<>();
        private final Map<UUID, String> permanent = new HashMap<>();

        private Builder() {}

        public Builder succeed(ClaimedJob job) {
            succeeded.add(job.id());
            failures.remove(job.id());
            permanent.remove(job.id());
            return this;
        }

        /** Retryable failure: the attempt counts and the job runs again after the backoff. */
        public Builder fail(ClaimedJob job, String reason) {
            succeeded.remove(job.id());
            permanent.remove(job.id());
            failures.put(job.id(), reason == null ? "Job failed" : reason);
            return this;
        }

        /** Retrying cannot help: the job is dead-lettered right away (unless a newer change was coalesced meanwhile). */
        public Builder failPermanently(ClaimedJob job, String reason) {
            succeeded.remove(job.id());
            failures.remove(job.id());
            permanent.put(job.id(), reason == null ? "Job failed permanently" : reason);
            return this;
        }

        public JobBatchResult build() { return new JobBatchResult(succeeded, failures, permanent); }
    }
}
