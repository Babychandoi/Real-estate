package com.company.bds.analytics.domain;

/** Classification kept per analytics device (anonymous id) in {@code analytics_client_flags}. */
public enum DeviceFlag {
    /** A staff account used this device: its traffic is internal. */
    INTERNAL,
    /** The behavioural rule saw automated traffic from this device. */
    BOT
}
