package com.company.bds.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProductionSafetyValidator implements ApplicationRunner {
    private final String mode;
    private final String databasePassword;
    private final String redisPassword;
    private final String allowedOrigins;
    private final boolean realKyc;
    private final boolean realTransactions;
    private final boolean outboxEnabled;
    private final String outboxUrl;
    private final String outboxSigningKey;
    private final String mediaAllowedHosts;
    private final boolean mediaStorageEnabled;
    private final String mediaAccessKey;
    private final String mediaSecretKey;
    private final String mediaBucket;

    public ProductionSafetyValidator(@Value("${app.mode:demo}") String mode,
                                     @Value("${spring.datasource.password:}") String databasePassword,
                                     @Value("${spring.data.redis.password:}") String redisPassword,
                                     @Value("${app.security.allowed-origins:}") String allowedOrigins,
                                     @Value("${app.features.real-kyc:false}") boolean realKyc,
                                     @Value("${app.features.real-transactions:false}") boolean realTransactions,
                                     @Value("${app.outbox.enabled:false}") boolean outboxEnabled,
                                     @Value("${app.outbox.webhook-url:}") String outboxUrl,
                                     @Value("${app.outbox.signing-key:}") String outboxSigningKey,
                                     @Value("${app.media.allowed-hosts:}") String mediaAllowedHosts,
                                     @Value("${app.media.storage-enabled:false}") boolean mediaStorageEnabled,
                                     @Value("${app.media.access-key:}") String mediaAccessKey,
                                     @Value("${app.media.secret-key:}") String mediaSecretKey,
                                     @Value("${app.media.bucket:}") String mediaBucket) {
        this.mode = mode;
        this.databasePassword = databasePassword;
        this.redisPassword = redisPassword;
        this.allowedOrigins = allowedOrigins;
        this.realKyc = realKyc;
        this.realTransactions = realTransactions;
        this.outboxEnabled = outboxEnabled;
        this.outboxUrl = outboxUrl;
        this.outboxSigningKey = outboxSigningKey;
        this.mediaAllowedHosts = mediaAllowedHosts;
        this.mediaStorageEnabled = mediaStorageEnabled;
        this.mediaAccessKey = mediaAccessKey;
        this.mediaSecretKey = mediaSecretKey;
        this.mediaBucket = mediaBucket;
    }

    @Override public void run(ApplicationArguments args) {
        if ("production".equalsIgnoreCase(mode) &&
                (databasePassword.isBlank() || databasePassword.contains("do-not-deploy") || databasePassword.length() < 16)) {
            throw new IllegalStateException("Production từ chối khởi động: mật khẩu cơ sở dữ liệu phải là secret mạnh (tối thiểu 16 ký tự). ");
        }
        if (!"production".equalsIgnoreCase(mode)) return;
        if (redisPassword.length() < 16) {
            throw new IllegalStateException("Production từ chối khởi động: Redis phải dùng secret tối thiểu 16 ký tự.");
        }
        if (java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .anyMatch(origin -> !origin.startsWith("https://"))) {
            throw new IllegalStateException("Production từ chối khởi động: mọi CORS origin phải dùng HTTPS.");
        }
        if (realKyc || realTransactions) {
            throw new IllegalStateException("Production từ chối bật eKYC/giao dịch: chưa có provider adapter được nghiệm thu.");
        }
        if (outboxEnabled && (!outboxUrl.startsWith("https://") || outboxSigningKey.length() < 32)) {
            throw new IllegalStateException("Production từ chối bật outbox: webhook HTTPS và signing key >=32 ký tự là bắt buộc.");
        }
        if (!mediaStorageEnabled || mediaAccessKey.isBlank() || mediaSecretKey.length() < 16 || mediaBucket.isBlank()) {
            throw new IllegalStateException("Production yêu cầu MinIO và credential an toàn.");
        }
        if (mediaAllowedHosts.contains("unsplash.com") || mediaAllowedHosts.contains("googleusercontent.com")) {
            throw new IllegalStateException("Production từ chối khởi động: phải cấu hình hostname object storage/CDN do dự án kiểm soát.");
        }
    }
}
