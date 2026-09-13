package com.company.bds.cms.api.request;

import jakarta.validation.constraints.NotBlank;

public class RejectRevisionRequest {

    @NotBlank(message = "Lý do trả về không được để trống")
    private String reason;

    public RejectRevisionRequest() {}

    public RejectRevisionRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
