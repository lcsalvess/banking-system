package com.lucas.sistemabancario.entity;

import com.lucas.sistemabancario.entity.enums.TipoConta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class ContaPoupanca extends Conta {
    private static final BigDecimal TAXA_RENDIMENTO = new BigDecimal("0.005");
    @Column(nullable = false)
    private LocalDate dataUltimoRendimento;

    public ContaPoupanca() {
    }

    public ContaPoupanca(Cliente titular, String numeroConta, LocalDate dataUltimoRendimento) {
        super(titular, numeroConta, TipoConta.POUPANCA);
        this.dataUltimoRendimento = dataUltimoRendimento;
    }

    public LocalDate getDataUltimoRendimento() {
        return dataUltimoRendimento;
    }

    public void atualizarDataUltimoRendimento() {
        this.dataUltimoRendimento = LocalDate.now();
    }

    public boolean podeReceberRendimento() {
        return !LocalDate.now().isBefore(dataUltimoRendimento.plusMonths(1));
    }

    public BigDecimal calcularRendimento() {
        return getSaldo().multiply(TAXA_RENDIMENTO);
    }

}
