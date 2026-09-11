package com.company.bds.transaction.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản ghi biến động ký quỹ phục vụ kiểm toán tài chính (FR28)
 */
public class EscrowTransaction {
    private final UUID id;
    private final UUID contractId;
    private final EscrowAction action;
    private final BigDecimal amount;
    private final UUID performedBy;
    private final String note;
    private final Instant createdAt;

    public EscrowTransaction(
            UUID id,
            UUID contractId,
            EscrowAction action,
            BigDecimal amount,
            UUID performedBy,
            String note,
            Instant createdAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.contractId = contractId;
        this.action = action;
        this.amount = amount;
        this.performedBy = performedBy;
        this.note = note;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getContractId() {
        return contractId;
    }

    public EscrowAction getAction() {
        return action;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getPerformedBy() {
        return performedBy;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
