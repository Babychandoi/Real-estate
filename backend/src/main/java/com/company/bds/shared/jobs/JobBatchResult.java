package com.company.bds.shared.jobs;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Outcome of one {@link JobHandler#handle} call: succeeded ids plus an optional reason per failed id. */
public record JobBatchResult(Set<UUID> succeeded, Map<UUID, String> failures) {

    public JobBatchResult {
        succeeded = Set.copyOf(succeeded);
        failures = Map.copyOf(failures);
    }

    public static JobBatchResult allSucceeded(Collection<ClaimedJob> jobs) {
        return new JobBatchResult(jobs.stream().map(ClaimedJob::id).collect(java.util.stream.Collectors.toSet()), Map.of());
    }

    public static JobBatchResult succeeded(Collection<UUID> ids) {
        return new JobBatchResult(new HashSet<>(ids), Map.of());
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Set<UUID> succeeded = new HashSet<>();
        private final Map<UUID, String> failures = new HashMap<>();

        private Builder() {}

        public Builder succeed(ClaimedJob job) {
            succeeded.add(job.id());
            failures.remove(job.id());
            return this;
        }

        public Builder fail(ClaimedJob job, String reason) {
            succeeded.remove(job.id());
            failures.put(job.id(), reason == null ? "Job failed" : reason);
            return this;
        }

        public JobBatchResult build() { return new JobBatchResult(succeeded, failures); }
    }
}
