package com.company.bds.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ShowcasePolicy {
    private final UUID ownerId;

    public ShowcasePolicy(@Value("${app.demo.showcase-owner-id:70000000-0000-0000-0000-000000000001}") UUID ownerId) {
        this.ownerId = ownerId;
    }

    public boolean isShowcaseOwner(UUID candidate) {
        return ownerId.equals(candidate);
    }
}
