package com.company.bds.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Cung cấp bean Clock tiêu chuẩn cho toàn bộ thời gian nghiệp vụ của hệ thống.
 * Tham khảo: PROJECT_CODE_RULES_BDS.md mục 4.4
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
