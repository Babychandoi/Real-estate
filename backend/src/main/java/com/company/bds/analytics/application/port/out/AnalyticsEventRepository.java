package com.company.bds.analytics.application.port.out;

import com.company.bds.analytics.domain.AnalyticsEvent;
import com.company.bds.analytics.domain.DeviceFlag;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Output port for storing analytics facts (append-only; a repeated event id is ignored). */
public interface AnalyticsEventRepository {

    /** Stores web events; returns how many were new (the rest were duplicates). */
    int insertWebEvents(List<AnalyticsEvent> events);

    /**
     * Stores one server event in the caller's transaction; returns whether it was new. The event is marked internal when
     * its user holds a staff role.
     */
    boolean insertServerEvent(AnalyticsEvent event);

    /** Flags known for these devices (anonymous ids); devices without a flag are absent from the map. */
    Map<String, Set<DeviceFlag>> deviceFlags(Collection<String> anonymousIds);

    /**
     * Flags a device and, when the flag is new, re-marks the device's stored events (internal or bot).
     *
     * @return whether the flag was new
     */
    boolean flagDevice(String anonymousId, DeviceFlag flag, String reason);
}
