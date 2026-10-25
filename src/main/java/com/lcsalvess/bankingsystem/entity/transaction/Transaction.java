package com.lcsalvess.bankingsystem.entity.transaction;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    /**
     * Saldo da conta imediatamente após esta transação.
     * É gravado na mesma transação de banco da alteração do saldo,
     * com a linha da conta travada.
     */
    @NotNull
    @Column(
            name = "balance_after",
            nullable = false,
            updatable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal balanceAfter;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "yield_date")
    private LocalDate yieldDate;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_transactions_account")
    )
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "performed_by_user_id",
            foreignKey = @ForeignKey(
                    name = "fk_transactions_performed_by_user"
            )
    )
    private User performedBy;

    @Column(name = "performed_by_system", nullable = false)
    private boolean performedBySystem;

    /**
     * Construtor exigido pelo JPA. Não é público para que nenhuma outra
     * parte do código consiga criar uma transação sem passar pelas
     * factories, que validam os dados.
     */
    protected Transaction() {
    }

    /**
     * Cria uma transação realizada por um usuário.
     */
    public static Transaction create(
            TransactionType type,
            BigDecimal amount,
            LocalDateTime createdAt,
            Account account,
            User performedBy,
            BigDecimal balanceAfter
    ) {
        requireOrdinaryOperationType(type);

        BigDecimal validatedBalance =
                requireValidBalanceAfter(balanceAfter);

        User validatedActor = requireHumanActor(performedBy);

        return new Transaction(
                UUID.randomUUID(),
                null,
                type,
                amount,
                validatedBalance,
                truncateToMicros(createdAt),
                null,
                account,
                validatedActor,
                false
        );
    }

    /**
     * Cria uma transação de transferência realizada por um usuário.
     */
    public static Transaction createTransfer(
            TransactionType type,
            BigDecimal amount,
            LocalDateTime createdAt,
            Account account,
            UUID transferCode,
            User performedBy,
            BigDecimal balanceAfter
    ) {
        requireTransferType(type);

        UUID validatedTransferCode = Objects.requireNonNull(
                transferCode,
                "Transfer transactions must have a transfer code."
        );

        BigDecimal validatedBalance =
                requireValidBalanceAfter(balanceAfter);

        User validatedActor = requireHumanActor(performedBy);

        return new Transaction(
                UUID.randomUUID(),
                validatedTransferCode,
                type,
                amount,
                validatedBalance,
                truncateToMicros(createdAt),
                null,
                account,
                validatedActor,
                false
        );
    }

    /**
     * Cria uma transação de rendimento gerada pelo sistema.
     */
    public static Transaction createSystemYield(
            BigDecimal amount,
            LocalDateTime createdAt,
            LocalDate yieldDate,
            Account account,
            BigDecimal balanceAfter
    ) {
        validateSystemYieldDate(yieldDate);

        BigDecimal validatedBalance =
                requireValidBalanceAfter(balanceAfter);

        return new Transaction(
                UUID.randomUUID(),
                null,
                TransactionType.YIELD,
                amount,
                validatedBalance,
                truncateToMicros(createdAt),
                yieldDate,
                account,
                null,
                true
        );
    }

    /**
     * Construtor interno sem validações ou operações que lancem
     * exceções durante a inicialização do objeto.
     */
    private Transaction(
            UUID transactionCode,
            UUID transferCode,
            TransactionType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            LocalDateTime createdAt,
            LocalDate yieldDate,
            Account account,
            User performedBy,
            boolean performedBySystem
    ) {
        this.transactionCode = transactionCode;
        this.transferCode = transferCode;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
        this.yieldDate = yieldDate;
        this.account = account;
        this.performedBy = performedBy;
        this.performedBySystem = performedBySystem;
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

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
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

    /**
     * O PostgreSQL armazena timestamps com precisão de microssegundos.
     * A truncagem garante que o valor em memória seja compatível
     * com a precisão persistida.
     */
    private static LocalDateTime truncateToMicros(
            LocalDateTime value
    ) {
        return value == null
                ? null
                : value.truncatedTo(ChronoUnit.MICROS);
    }

    private static BigDecimal requireValidBalanceAfter(
            BigDecimal balanceAfter
    ) {
        Objects.requireNonNull(
                balanceAfter,
                "The balance after the transaction is required."
        );

        if (balanceAfter.signum() < 0
                || balanceAfter.compareTo(Account.MAX_BALANCE) > 0) {
            throw new IllegalArgumentException(
                    "The balance after the transaction is out of range."
            );
        }

        return balanceAfter;
    }

    /**
     * Deposits and withdrawals do not carry transfer metadata. Transfers
     * must be created through {@link #createTransfer}.
     */
    private static void requireOrdinaryOperationType(TransactionType type) {
        if (type != TransactionType.DEPOSIT
                && type != TransactionType.WITHDRAWAL) {
            throw new IllegalArgumentException(
                    "Only DEPOSIT and WITHDRAWAL transactions can be created "
                            + "with create()."
            );
        }
    }

    /**
     * Transfer factories accept only the two sides of a transfer. Requiring
     * a transfer code ensures both records can be correlated as one operation.
     */
    private static void requireTransferType(TransactionType type) {
        if (type != TransactionType.TRANSFER_SENT
                && type != TransactionType.TRANSFER_RECEIVED) {
            throw new IllegalArgumentException(
                    "Only TRANSFER_SENT and TRANSFER_RECEIVED transactions "
                            + "can be created with createTransfer()."
            );
        }
    }

    private static User requireHumanActor(User performedBy) {
        return Objects.requireNonNull(
                performedBy,
                "Human transactions must have an associated user."
        );
    }

    private static void validateSystemYieldDate(
            LocalDate yieldDate
    ) {
        Objects.requireNonNull(
                yieldDate,
                "System-generated yield transactions require a yield date."
        );
    }
}