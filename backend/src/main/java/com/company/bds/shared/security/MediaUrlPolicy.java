package com.company.bds.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MediaUrlPolicy {
    private final Set<String> allowedHosts;

    public MediaUrlPolicy(@Value("${app.media.allowed-hosts:images.unsplash.com,lh3.googleusercontent.com}") String hosts) {
        this.allowedHosts = Arrays.stream(hosts.split(",")).map(String::trim).map(String::toLowerCase)
                .filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public void validate(List<String> urls) {
        if (urls == null) return;
        if (urls.size() > 20) throw new IllegalArgumentException("Mỗi tin đăng tối đa 20 ảnh.");
        for (String value : urls) {
            try {
                if (value != null && value.matches("^/api/v1/public/media/[0-9a-fA-F-]{36}\\.(jpg|png|webp|avif)$")) {
                    continue;
                }
                URI uri = URI.create(value);
                String host = uri.getHost();
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || host == null
                        || !allowedHosts.contains(host.toLowerCase())) {
                    throw new IllegalArgumentException("URL ảnh phải dùng HTTPS và thuộc kho media được phép.");
                }
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("URL ảnh không hợp lệ hoặc không thuộc kho media được phép.");
            }
        }
    }
}
