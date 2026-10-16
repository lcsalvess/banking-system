
package com.lcsalvess.bankingsystem.entity;

import com.lcsalvess.bankingsystem.entity.enums.AccountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "savings_accounts")
@PrimaryKeyJoinColumn(
        name = "id",
        foreignKey = @ForeignKey(name = "fk_savings_accounts_account")
)
public class SavingsAccount extends Account {

    private static final BigDecimal MONTHLY_YIELD_RATE =
            new BigDecimal("0.005");

    @NotNull
    @Column(name = "last_yield_date", nullable = false)
    private LocalDate lastYieldDate;

    public SavingsAccount() {
    }

    public SavingsAccount(
            Client client,
            String accountNumber,
            String accountDigit,
            LocalDate initialYieldDate
    ) {
        super(client, accountNumber, accountDigit, AccountType.SAVINGS);
        this.lastYieldDate = initialYieldDate;
    }

    public LocalDate getLastYieldDate() {
        return lastYieldDate;
    }

    public void updateLastYieldDate(LocalDate referenceDate) {
        if (referenceDate == null) {
            throw new IllegalArgumentException(
                    "A data de referência do rendimento é obrigatória."
            );
        }

        this.lastYieldDate = referenceDate;
    }

    public boolean isEligibleForYield(LocalDate referenceDate) {
        return referenceDate != null
                && referenceDate.isAfter(lastYieldDate);
    }


    public BigDecimal calculateYield() {
        double dailyRate = Math.pow(
                1.0 + MONTHLY_YIELD_RATE.doubleValue(),
                1.0 / 30.0
        ) - 1.0;

        return getBalance()
                .multiply(BigDecimal.valueOf(dailyRate))
                .setScale(2, RoundingMode.HALF_UP);
    }
}