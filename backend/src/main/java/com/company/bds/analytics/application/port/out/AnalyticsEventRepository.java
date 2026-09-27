package com.company.bds.analytics.application.port.out;

import com.company.bds.analytics.domain.AnalyticsEvent;

import java.util.List;

/** Output port for storing analytics facts (append-only; a repeated event id is ignored). */
public interface AnalyticsEventRepository {

    /** Stores web events; returns how many were new (the rest were duplicates). */
    int insertWebEvents(List<AnalyticsEvent> events);

    /**
     * Stores one server event in the caller's transaction; returns whether it was new. The event is marked internal when
     * its user holds a staff role.
     */
    boolean insertServerEvent(AnalyticsEvent event);
}
