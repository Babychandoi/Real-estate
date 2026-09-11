package com.company.bds.cms.api.request;

import jakarta.validation.constraints.NotBlank;

public class RejectRevisionRequest {

    @NotBlank(message = "Lý do trả về không được để trống")
    private String reason;

    private String adminUsername = "admin";

    public RejectRevisionRequest() {}

    public RejectRevisionRequest(String reason, String adminUsername) {
        this.reason = reason;
        this.adminUsername = adminUsername;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
}
