package com.company.bds.transaction.api.request;

import java.util.UUID;

public class DepositActionRequest {
    private UUID operatorId;
    private String reason;

    public DepositActionRequest() {}

    public DepositActionRequest(UUID operatorId, String reason) {
        this.operatorId = operatorId;
        this.reason = reason;
    }

    public UUID getOperatorId() { return operatorId; }
    public void setOperatorId(UUID operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
