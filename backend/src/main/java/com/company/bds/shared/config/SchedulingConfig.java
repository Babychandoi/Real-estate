package com.company.bds.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} tasks run unless {@code app.scheduling.enabled=false}, which one-off tooling runs (the UAT seeder)
 * set so they never sweep, sync or purge a shared database while they work.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {
}
