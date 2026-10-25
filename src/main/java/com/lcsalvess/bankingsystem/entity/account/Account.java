package com.lcsalvess.bankingsystem.entity.account;

import com.lcsalvess.bankingsystem.entity.client.Client;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(
        name = "accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_accounts_account_number", columnNames = "account_number")
        }
)
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Account {

    /**
     * Saldo máximo por conta. Mantém o valor dentro de NUMERIC(19,2) e dentro
     * dos 15 dígitos significativos que um double (clientes JS) representa
     * sem perda. Espelha a constraint ck_accounts_balance_max.
     */
    public static final BigDecimal MAX_BALANCE = new BigDecimal("9999999999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_accounts_client"))
    private Client client;
    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;
    @Column(name = "account_digit", nullable = false, length = 1)
    private String digit;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType type;

    protected Account() {
    }

    public Account(Client client, String accountNumber, String digit, AccountType type) {
        this.client = client;
        this.accountNumber = accountNumber;
        this.digit = digit;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public AccountType getType() {
        return type;
    }

    public Client getClient() {
        return client;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getDigit() {
        return digit;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    /**
     * Returns whether the amount can be credited without violating the
     * account's status, monetary precision, or maximum balance.
     */
    public boolean canCredit(BigDecimal amount) {
        return status == AccountStatus.ACTIVE
                && isValidAmount(amount)
                && balance.add(amount).compareTo(MAX_BALANCE) <= 0;
    }

    /**
     * Credits the account only when the operation preserves its invariants.
     * Application services still validate first to return API-specific errors.
     */
    public void credit(BigDecimal amount) {
        requireActive();
        BigDecimal validatedAmount = requireValidAmount(amount);

        if (!canCredit(validatedAmount)) {
            throw new IllegalStateException(
                    "Crediting this amount would exceed the account balance limit."
            );
        }

        this.balance = this.balance.add(validatedAmount);
    }

    /**
     * Debits the account only when the amount is positive and the balance
     * remains non-negative.
     */
    public void debit(BigDecimal amount) {
        requireActive();
        BigDecimal validatedAmount = requireValidAmount(amount);

        if (balance.compareTo(validatedAmount) < 0) {
            throw new IllegalStateException(
                    "The account has insufficient balance for this debit."
            );
        }

        this.balance = this.balance.subtract(validatedAmount);
    }

    /**
     * Cancellation is only valid for an active account with no remaining funds.
     */
    public void cancel() {
        requireActive();

        if (balance.signum() != 0) {
            throw new IllegalStateException(
                    "An account can only be cancelled with a zero balance."
            );
        }

        this.status = AccountStatus.CANCELLED;
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Operations are not allowed on an inactive account."
            );
        }
    }

    private static BigDecimal requireValidAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "The operation amount is required.");

        if (!isValidAmount(amount)) {
            throw new IllegalArgumentException(
                    "The operation amount must be positive and within the supported monetary range."
            );
        }

        return amount;
    }

    private static boolean isValidAmount(BigDecimal amount) {
        return amount != null
                && amount.signum() > 0
                && amount.scale() <= 2
                && amount.compareTo(MAX_BALANCE) <= 0;
    }

    public AccountStatus getStatus() {
        return status;
    }

}