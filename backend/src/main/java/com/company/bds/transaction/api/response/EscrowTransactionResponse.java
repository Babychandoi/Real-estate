package com.company.bds.transaction.api.response;

import com.company.bds.transaction.domain.model.EscrowAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class EscrowTransactionResponse {
    private UUID id;
    private EscrowAction action;
    private BigDecimal amount;
    private UUID performedBy;
    private String note;
    private Instant createdAt;

    public EscrowTransactionResponse() {}

    public EscrowTransactionResponse(UUID id, EscrowAction action, BigDecimal amount, UUID performedBy, String note, Instant createdAt) {
        this.id = id;
        this.action = action;
        this.amount = amount;
        this.performedBy = performedBy;
        this.note = note;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public EscrowAction getAction() { return action; }
    public BigDecimal getAmount() { return amount; }
    public UUID getPerformedBy() { return performedBy; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
