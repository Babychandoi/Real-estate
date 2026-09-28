package com.company.bds.notification.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by the engagement module: lets an unsubscribe link stop the alerts of one saved search without the
 * notification module knowing how saved searches are stored.
 */
public interface SavedSearchAlertsPort {

    /** Name of the user's saved search, if it still exists. */
    Optional<String> name(UUID userId, UUID savedSearchId);

    /** Turns the saved search's alerts off (frequency OFF); true when it existed. Idempotent. */
    boolean stopAlerts(UUID userId, UUID savedSearchId);
}
