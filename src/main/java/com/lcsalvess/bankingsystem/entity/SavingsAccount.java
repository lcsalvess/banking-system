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

    private static final BigDecimal DAILY_YIELD_RATE =
            BigDecimal.valueOf(
                    Math.pow(
                            1.0 + MONTHLY_YIELD_RATE.doubleValue(),
                            1.0 / 30.0
                    ) - 1.0
            );

    private static final int FINANCIAL_SCALE = 2;

    @NotNull
    @Column(name = "last_yield_date", nullable = false)
    private LocalDate lastYieldDate;

    @NotNull
    @Column(name = "last_yield_processed_date", nullable = false)
    private LocalDate lastYieldProcessedDate;

    protected SavingsAccount() {
    }

    public SavingsAccount(
            Client client,
            String accountNumber,
            String accountDigit,
            LocalDate initialYieldDate
    ) {
        super(client, accountNumber, accountDigit, AccountType.SAVINGS);

        validateDateNotNull(
                initialYieldDate,
                "A data inicial do rendimento é obrigatória."
        );

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
     * Atualiza a data do último rendimento registrado.
     */
    public void updateLastYieldDate(LocalDate referenceDate) {
        validateDateNotNull(
                referenceDate,
                "A data de referência do rendimento é obrigatória."
        );

        if (lastYieldDate != null
                && referenceDate.isBefore(lastYieldDate)) {
            throw new IllegalArgumentException(
                    "A data do último rendimento não pode retroceder."
            );
        }

        this.lastYieldDate = referenceDate;
    }

    /**
     * Verifica se a conta está elegível para rendimento na data informada.
     */
    public boolean isEligibleForYield(LocalDate referenceDate) {
        return referenceDate != null
                && lastYieldDate != null
                && referenceDate.isAfter(lastYieldDate);
    }

    /**
     * Calcula o rendimento diário com base no saldo histórico informado.
     * O valor monetário final é arredondado para duas casas decimais.
     */
    public BigDecimal calculateYield(
            BigDecimal balanceAtReferenceDate
    ) {
        if (balanceAtReferenceDate == null
                || balanceAtReferenceDate.signum() < 0) {
            throw new IllegalArgumentException(
                    "O saldo histórico deve ser válido e não negativo."
            );
        }

        return balanceAtReferenceDate
                .multiply(DAILY_YIELD_RATE)
                .setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Marca a data como processada sem alterar a data do último rendimento.
     */
    public void markYieldDateProcessed(LocalDate referenceDate) {
        validateDateNotNull(
                referenceDate,
                "A data de processamento do rendimento é obrigatória."
        );

        if (lastYieldProcessedDate != null
                && referenceDate.isBefore(lastYieldProcessedDate)) {
            throw new IllegalArgumentException(
                    "A data processada não pode retroceder."
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