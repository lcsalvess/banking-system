package com.lcsalvess.bankingsystem.entity.account;

import com.lcsalvess.bankingsystem.entity.client.Client;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Represents a savings account with daily yield processing capabilities.
 *
 * <p>Maintains yield reference dates, determines eligibility for daily yield,
 * and calculates monetary returns based on historical account balances.
 */
@Entity
@Table(name = "savings_accounts")
@PrimaryKeyJoinColumn(
    name = "id",
    foreignKey = @ForeignKey(name = "fk_savings_accounts_account")
)
@DynamicUpdate
public class SavingsAccount extends Account {

  /**
   * Daily yield rate equivalent to 0.5% monthly interest compounded over 30 days:
   * (1 + 0.005)^(1/30) - 1.
   *
   * <p>This is a decimal constant rather than a calculation using {@code Math.pow},
   * ensuring that the credited amount does not depend on the JVM's floating-point
   * arithmetic.
   */
  private static final BigDecimal DAILY_YIELD_RATE =
      new BigDecimal("0.000166265204228477063643");

  private static final int FINANCIAL_SCALE = 2;

  @NotNull
  @Column(name = "last_yield_date", nullable = false)
  private LocalDate lastYieldDate;

  @NotNull
  @Column(name = "last_yield_processed_date", nullable = false)
  private LocalDate lastYieldProcessedDate;

  /**
   * Constructor required by JPA.
   */
  protected SavingsAccount() {
  }

  /**
   * Creates a savings account after validating the initial yield date.
   *
   * <p>Initializes both yield tracking dates using the supplied reference date.
   *
   * @param client the client who owns the account
   * @param accountNumber the generated account number
   * @param accountDigit the account check digit
   * @param initialYieldDate the initial yield reference date
   * @return the newly created savings account
   * @throws IllegalArgumentException if the initial yield date is null
   */
  public static SavingsAccount create(
      Client client,
      String accountNumber,
      String accountDigit,
      LocalDate initialYieldDate
  ) {
    validateDateNotNull(
        initialYieldDate,
        "The initial yield date is required."
    );

    return new SavingsAccount(
        client,
        accountNumber,
        accountDigit,
        initialYieldDate
    );
  }

  /**
   * Internal constructor that receives previously validated arguments.
   */
  private SavingsAccount(
      Client client,
      String accountNumber,
      String accountDigit,
      LocalDate initialYieldDate
  ) {
    super(client, accountNumber, accountDigit, AccountType.SAVINGS);

    this.lastYieldDate = initialYieldDate;
    this.lastYieldProcessedDate = initialYieldDate;
  }

  public LocalDate getLastYieldDate() {
    return lastYieldDate;
  }

  public LocalDate getLastYieldProcessedDate() {
    return lastYieldProcessedDate;
  }

  /**
   * Updates the date of the most recently recorded yield.
   *
   * <p>Prevents the last yield date from moving backwards.
   *
   * @param referenceDate the reference date of the most recently recorded yield
   * @throws IllegalArgumentException if the reference date is null or precedes
   *     the currently recorded yield date
   */
  public void updateLastYieldDate(LocalDate referenceDate) {
    validateDateNotNull(
        referenceDate,
        "The yield reference date is required."
    );

    if (lastYieldDate != null
        && referenceDate.isBefore(lastYieldDate)) {
      throw new IllegalArgumentException(
          "The last yield date cannot move backwards."
      );
    }

    this.lastYieldDate = referenceDate;
  }

  /**
   * Checks whether the account is eligible to earn yield on the specified date.
   *
   * <p>The account is eligible when the reference date is later than
   * the most recently recorded yield date.
   *
   * @param referenceDate the date on which yield eligibility is evaluated
   * @return {@code true} if the account is eligible; {@code false} otherwise
   */
  public boolean isEligibleForYield(LocalDate referenceDate) {
    return referenceDate != null
        && lastYieldDate != null
        && referenceDate.isAfter(lastYieldDate);
  }

  /**
   * Calculates the daily yield based on the supplied historical balance.
   *
   * <p>Applies the configured daily yield rate and rounds the resulting
   * monetary amount to two decimal places using {@link RoundingMode#HALF_UP}.
   *
   * @param balanceAtReferenceDate the account balance at the reference date
   * @return the calculated daily yield rounded to two decimal places
   * @throws IllegalArgumentException if the historical balance is null or negative
   */
  public BigDecimal calculateYield(
      BigDecimal balanceAtReferenceDate
  ) {
    if (balanceAtReferenceDate == null
        || balanceAtReferenceDate.signum() < 0) {
      throw new IllegalArgumentException(
          "The historical balance must be valid and non-negative."
      );
    }

    return balanceAtReferenceDate
        .multiply(DAILY_YIELD_RATE)
        .setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * Marks a yield reference date as processed without changing the last yield date.
   *
   * <p>Prevents the processed date from moving backwards.
   *
   * @param referenceDate the date marked as processed
   * @throws IllegalArgumentException if the reference date is null or precedes
   *     the currently recorded processed date
   */
  public void markYieldDateProcessed(LocalDate referenceDate) {
    validateDateNotNull(
        referenceDate,
        "The yield processing date is required."
    );

    if (lastYieldProcessedDate != null
        && referenceDate.isBefore(lastYieldProcessedDate)) {
      throw new IllegalArgumentException(
          "The processed date cannot move backwards."
      );
    }

    this.lastYieldProcessedDate = referenceDate;
  }

  private static void validateDateNotNull(
      LocalDate date,
      String message
  ) {
    if (date == null) {
      throw new IllegalArgumentException(message);
    }
  }
}
