package com.lcsalvess.bankingsystem.entity.account;

import com.lcsalvess.bankingsystem.entity.client.Client;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

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
     */
    public boolean isEligibleForYield(LocalDate referenceDate) {
        return referenceDate != null
                && lastYieldDate != null
                && referenceDate.isAfter(lastYieldDate);
    }

    /**
     * Calculates the daily yield based on the supplied historical balance.
     * The final monetary amount is rounded to two decimal places.
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
     * Marks the date as processed without changing the last yield date.
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
