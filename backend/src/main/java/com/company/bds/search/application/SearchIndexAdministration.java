package com.company.bds.search.application;

import java.time.Instant;
import java.util.List;

/** Admin use cases for the search index (contract §9 rebuild with alias swap and rollback). */
public interface SearchIndexAdministration {

    Status status();

    /** Creates a new index, dual-writes to it and backfills it in the background; swaps the alias when done. */
    Status startRebuild();

    /** Points the alias back at the PREVIOUS index (kept and dual-written since the last swap). */
    Status rollback();

    /** Retires PREVIOUS (stops writing it) and deletes indices retired more than two minutes ago. */
    Status cleanup();

    record IndexInfo(String name, String role, int mappingVersion, long backfilledRows, Instant backfillCompletedAt,
                     Instant activatedAt, Instant retiredAt, Instant createdAt, long documents) {}

    record Status(boolean enabled, boolean ready, String alias, List<String> aliasTargets, List<IndexInfo> indices,
                  long readModelRows, long pendingJobs, Double oldestPendingJobSeconds) {}
}
