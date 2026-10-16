package com.lcsalvess.bankingsystem.entity;

import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
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
    @Column(name = "transfer_code", updatable = false)
    private UUID transferCode;
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
    @Column(name = "yield_date")
    private LocalDate yieldDate;
    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_transactions_account"))
    private Account account;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "performed_by_user_id",
            foreignKey = @ForeignKey(name = "fk_transactions_performed_by_user")
    )
    private User performedBy;

    @Column(name = "performed_by_system", nullable = false)
    private boolean performedBySystem;

    public Transaction() {
    }

    public Transaction(
            TransactionType type,
            BigDecimal amount,
            LocalDateTime createdAt,
            Account account,
            User performedBy
    ) {
        this.transactionCode = UUID.randomUUID();
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
        this.account = account;
        this.performedBy = requireHumanActor(performedBy);
    }

    public Transaction(
            TransactionType type,
            BigDecimal amount,
            LocalDateTime createdAt,
            Account account,
            UUID transferCode,
            User performedBy
    ) {
        this.transactionCode = UUID.randomUUID();
        this.transferCode = transferCode;
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
        this.account = account;
        this.performedBy = requireHumanActor(performedBy);
    }

    public Transaction(
            TransactionType type,
            BigDecimal amount,
            LocalDateTime createdAt,
            LocalDate yieldDate,
            Account account,
            User performedBy
    ) {
        this.transactionCode = UUID.randomUUID();
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
        this.yieldDate = yieldDate;
        this.account = account;
        this.performedBy = requireHumanActor(performedBy);
    }

    public Transaction(
            TransactionType type,
            BigDecimal amount,
            LocalDateTime createdAt,
            LocalDate yieldDate,
            Account account
    ) {
        if (type != TransactionType.YIELD) {
            throw new IllegalArgumentException(
                    "System-generated transactions must be of type YIELD."
            );
        }

        if (yieldDate == null) {
            throw new IllegalArgumentException(
                    "System-generated yield transactions require a yield date."
            );
        }

        this.transactionCode = UUID.randomUUID();
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
        this.yieldDate = yieldDate;
        this.account = account;
        this.performedBy = null;
        this.performedBySystem = true;
    }

    public Long getId() {
        return id;
    }

    public UUID getTransactionCode() {
        return transactionCode;
    }

    public UUID getTransferCode() {
        return transferCode;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDate getYieldDate() {
        return yieldDate;
    }

    public boolean isPerformedBySystem() {
        return performedBySystem;
    }

    public Account getAccount() {
        return account;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    private User requireHumanActor(User performedBy) {
        return Objects.requireNonNull(
                performedBy,
                "Human transactions must have an associated user."
        );
    }
}
