package com.telecom.billing.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** unique (account_id, cycle): resuming or re-running a bill run never double-bills. */
@Entity
@Table(name = "invoices",
       uniqueConstraints = @UniqueConstraint(name = "uk_invoices_account_cycle", columnNames = {"account_id", "cycle"}),
       indexes = @Index(name = "idx_invoices_cycle", columnList = "cycle"))
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, length = 20)
    private String accountId;

    @Column(nullable = false, length = 7)
    private String cycle;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Invoice() {
    }

    public Invoice(String accountId, String cycle, BigDecimal amount) {
        this.accountId = accountId;
        this.cycle = cycle;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getCycle() { return cycle; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
