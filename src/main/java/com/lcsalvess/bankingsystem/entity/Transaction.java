package com.lcsalvess.bankingsystem.entity;

import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transactions_code",
                        columnNames = "transaction_code"
                )
        }
)
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    @Column(name = "transaction_code", nullable = false, updatable = false)
    private UUID transactionCode;
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType type;
    @NotNull
    @Positive
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false,
    foreignKey = @ForeignKey(name = "fk_transactions_account"))
    private Account account;

    public Transaction() {}

    public Transaction(TransactionType type, BigDecimal amount, LocalDateTime createdAt, Account account) {
        this.transactionCode = UUID.randomUUID();
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
        this.account = account;
    }

    public Long getId() {
        return id;
    }

    public UUID getTransactionCode() { return transactionCode; }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Account getAccount() {
        return account;
    }
}
