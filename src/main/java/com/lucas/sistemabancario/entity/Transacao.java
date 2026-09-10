package com.lucas.sistemabancario.entity;

import com.lucas.sistemabancario.entity.enums.TipoTransacao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Transacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransacao tipoTransacao;
    @NotNull
    @Positive
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;
    @NotNull
    @Column(nullable = false)
    private LocalDateTime dataHora;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    public Transacao() {}

    public Transacao(TipoTransacao tipoTransacao, BigDecimal valor, LocalDateTime dataHora, Conta conta) {
        this.tipoTransacao = tipoTransacao;
        this.valor = valor;
        this.dataHora = dataHora;
        this.conta = conta;
    }

    public Long getId() {
        return id;
    }

    public TipoTransacao getTipoTransacao() {
        return tipoTransacao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public Conta getConta() {
        return conta;
    }
}
