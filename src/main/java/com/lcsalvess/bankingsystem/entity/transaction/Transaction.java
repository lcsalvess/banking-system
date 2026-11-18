package com.lcsalvess.bankingsystem.entity.transaction;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.util.time.InstantPrecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a financial transaction associated with a bank account.
 *
 * <p>Stores transaction details, including its unique identifier, type,
 * amount, resulting account balance, timestamp, and responsible actor.
 *
 * <p>Supports deposits, withdrawals, transfers, and system-generated
 * yield transactions through validated factory methods.
 */
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
   * Account balance immediately after this transaction.
   * It is persisted in the same database transaction as the balance update,
   * while the account row is locked.
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
  private Instant createdAt;

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
      foreignKey = @ForeignKey(name = "fk_transactions_performed_by_user")
  )
  private User performedBy;

  @Column(name = "performed_by_system", nullable = false)
  private boolean performedBySystem;

  /**
   * Constructor required by JPA. It is not public so that no other part
   * of the code can create a transaction without going through the
   * factories, which validate the data.
   */
  protected Transaction() {
  }

  /**
   * Creates a transaction performed by an authenticated user.
   *
   * <p>Accepts deposit and withdrawal operations and validates the
   * transaction data before creating the entity.
   *
   * @param type the transaction type (deposit or withdrawal)
   * @param amount the positive transaction amount
   * @param createdAt the transaction creation timestamp
   * @param account the account associated with the transaction
   * @param performedBy the user responsible for the transaction
   * @param balanceAfter the account balance immediately after the transaction
   * @return the newly created transaction
   * @throws IllegalArgumentException if the transaction type or monetary values
   *     are invalid
   * @throws NullPointerException if a required argument is null
   */
  public static Transaction create(
      TransactionType type,
      BigDecimal amount,
      Instant createdAt,
      Account account,
      User performedBy,
      BigDecimal balanceAfter
  ) {
    requireOrdinaryOperationType(type);

    BigDecimal validatedAmount = requireValidAmount(amount);
    Instant validatedCreatedAt = validateCreatedAt(createdAt);
    Account validatedAccount = requireAccount(account);
    BigDecimal validatedBalance = requireValidBalanceAfter(balanceAfter);
    User validatedActor = requireHumanActor(performedBy);

    return new Transaction(
        UUID.randomUUID(),
        null,
        type,
        validatedAmount,
        validatedBalance,
        validatedCreatedAt,
        null,
        validatedAccount,
        validatedActor,
        false
    );
  }

  /**
   * Creates a transfer transaction performed by an authenticated user.
   *
   * <p>Accepts sent and received transfer operations and associates the
   * resulting transaction with a shared transfer code.
   *
   * @param type the transfer transaction type
   * @param amount the positive transaction amount
   * @param createdAt the transaction creation timestamp
   * @param account the account associated with the transaction
   * @param transferCode the identifier shared by both sides of the transfer
   * @param performedBy the user responsible for the transfer
   * @param balanceAfter the account balance immediately after the transaction
   * @return the newly created transfer transaction
   * @throws IllegalArgumentException if the transaction type or monetary values
   *     are invalid
   * @throws NullPointerException if a required argument is null
   */
  public static Transaction createTransfer(
      TransactionType type,
      BigDecimal amount,
      Instant createdAt,
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

    BigDecimal validatedAmount = requireValidAmount(amount);
    Instant validatedCreatedAt = validateCreatedAt(createdAt);
    Account validatedAccount = requireAccount(account);
    BigDecimal validatedBalance = requireValidBalanceAfter(balanceAfter);
    User validatedActor = requireHumanActor(performedBy);

    return new Transaction(
        UUID.randomUUID(),
        validatedTransferCode,
        type,
        validatedAmount,
        validatedBalance,
        validatedCreatedAt,
        null,
        validatedAccount,
        validatedActor,
        false
    );
  }

  /**
   * Creates a system-generated yield transaction.
   *
   * <p>Associates the transaction with a savings account and its corresponding
   * yield reference date. System-generated transactions have no associated
   * human actor.
   *
   * @param amount the positive yield amount
   * @param createdAt the transaction creation timestamp
   * @param yieldDate the reference date for the generated yield
   * @param account the savings account receiving the yield
   * @param balanceAfter the account balance immediately after the transaction
   * @return the newly created system-generated yield transaction
   * @throws IllegalArgumentException if the account is not a savings account
   *     or the monetary values are invalid
   * @throws NullPointerException if a required argument is null
   */
  public static Transaction createSystemYield(
      BigDecimal amount,
      Instant createdAt,
      LocalDate yieldDate,
      Account account,
      BigDecimal balanceAfter
  ) {
    validateSystemYieldDate(yieldDate);

    BigDecimal validatedAmount = requireValidAmount(amount);
    Instant validatedCreatedAt = validateCreatedAt(createdAt);
    SavingsAccount validatedAccount = requireSavingsAccount(account);
    BigDecimal validatedBalance = requireValidBalanceAfter(balanceAfter);

    return new Transaction(
        UUID.randomUUID(),
        null,
        TransactionType.YIELD,
        validatedAmount,
        validatedBalance,
        validatedCreatedAt,
        yieldDate,
        validatedAccount,
        null,
        true
    );
  }

  /**
   * Internal constructor without validations or operations that may throw
   * exceptions during object initialization.
   */
  private Transaction(
      UUID transactionCode,
      UUID transferCode,
      TransactionType type,
      BigDecimal amount,
      BigDecimal balanceAfter,
      Instant createdAt,
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

  public Instant getCreatedAt() {
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

  private static Instant validateCreatedAt(Instant createdAt) {
    return InstantPrecision.truncateToMicros(
        createdAt,
        "The transaction creation timestamp is required."
    );
  }

  private static Account requireAccount(Account account) {
    return Objects.requireNonNull(
        account,
        "The transaction account is required."
    );
  }

  private static SavingsAccount requireSavingsAccount(Account account) {
    Account validatedAccount = requireAccount(account);

    if (!(validatedAccount instanceof SavingsAccount savingsAccount)) {
      throw new IllegalArgumentException(
          "System-generated yield transactions require a savings account."
      );
    }

    return savingsAccount;
  }

  private static BigDecimal requireValidAmount(BigDecimal amount) {
    Objects.requireNonNull(
        amount,
        "The transaction amount is required."
    );

    if (amount.signum() <= 0
        || amount.scale() > 2
        || amount.compareTo(Account.MAX_BALANCE) > 0) {
      throw new IllegalArgumentException(
          "The transaction amount must be positive and within the supported monetary range."
      );
    }

    return amount;
  }

  private static BigDecimal requireValidBalanceAfter(
      BigDecimal balanceAfter
  ) {
    Objects.requireNonNull(
        balanceAfter,
        "The balance after the transaction is required."
    );

    if (balanceAfter.signum() < 0
        || balanceAfter.scale() > 2
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
