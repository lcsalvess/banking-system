package com.lucas.sistemabancario.entity;

import com.lucas.sistemabancario.entity.enums.AccountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name= "savings_account")
@PrimaryKeyJoinColumn(
        name = "id",
        foreignKey = @ForeignKey(name = "fk_savings_account_account")
)
public class SavingsAccount extends Account {
    private static final BigDecimal YIELD_RATE = new BigDecimal("0.005");
    @NotNull
    @Column(name = "last_yield_date", nullable = false)
    private LocalDate lastYieldDate;

    public SavingsAccount() {
    }

    public SavingsAccount(
            Client client,
            String accountNumber,
            String accountDigit
    ) {
        super(client, accountNumber, accountDigit, AccountType.SAVINGS);
        this.lastYieldDate = lastYieldDate;
    }

    public LocalDate getLastYieldDate() {
        return lastYieldDate;
    }

    public void updateLastYieldDate() {
        this.lastYieldDate = LocalDate.now();
    }

    public boolean isEligibleForYield() {
        return !LocalDate.now().isBefore(lastYieldDate.plusMonths(1));
    }

    public BigDecimal calculateYield() {
        return getBalance().multiply(YIELD_RATE).setScale(2, RoundingMode.HALF_UP);
    }

}
