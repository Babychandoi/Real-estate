package com.company.bds.transaction.infrastructure.persistence.entity;

import com.company.bds.transaction.domain.model.EscrowAction;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "escrow_transactions")
public class EscrowTransactionJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private DepositContractJpaEntity contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private EscrowAction action;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "performed_by", nullable = false)
    private UUID performedBy;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public EscrowTransactionJpaEntity() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public DepositContractJpaEntity getContract() { return contract; }
    public void setContract(DepositContractJpaEntity contract) { this.contract = contract; }
    public EscrowAction getAction() { return action; }
    public void setAction(EscrowAction action) { this.action = action; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public UUID getPerformedBy() { return performedBy; }
    public void setPerformedBy(UUID performedBy) { this.performedBy = performedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
