package com.company.bds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point chính của ứng dụng Nền tảng Bất động sản BDS WF 2026.
 * Kiến trúc Modular Monolith tuân thủ PROJECT_CODE_RULES_BDS.md.
 */
@SpringBootApplication
@EnableScheduling
public class BdsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BdsApplication.class, args);
    }
}
