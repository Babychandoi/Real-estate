package com.company.bds.lead.application;

import com.company.bds.shared.error.ApiException;

import java.util.UUID;

/** The lead changed since the client read it (stale {@code expectedVersion}); the client reloads and retries. */
public class LeadVersionConflictException extends ApiException {
    private final UUID leadId;

    public LeadVersionConflictException(UUID leadId) {
        super(409, "LEAD_VERSION_CONFLICT",
                "Yêu cầu vừa được người khác cập nhật. Đã tải lại dữ liệu mới nhất, vui lòng kiểm tra rồi thử lại.");
        this.leadId = leadId;
    }

    public UUID leadId() { return leadId; }
}
