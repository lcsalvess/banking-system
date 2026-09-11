package com.lucas.sistemabancario.entity;

import com.lucas.sistemabancario.entity.enums.SituacaoConta;
import com.lucas.sistemabancario.entity.enums.TipoConta;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Conta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "titular_id", nullable = false)
    private Cliente titular;
    @Column(nullable = false, unique = true, length = 6)
    private String numeroConta;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SituacaoConta situacaoConta = SituacaoConta.ATIVA;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoConta tipoConta;

    public Conta () {}

    public Conta(Cliente titular, String numeroConta, TipoConta tipoConta) {
        this.titular = titular;
        this.numeroConta = numeroConta;
        this.tipoConta = tipoConta;
    }

    public Long getId() {
        return id;
    }

    public TipoConta getTipoConta() {
        return tipoConta;
    }
    
    public Cliente getTitular() {
        return titular;
    }

    public void setTitular(Cliente titular) {
        this.titular = titular;
    }

    public String getNumeroConta() {
        return numeroConta;
    }

    public void setNumeroConta(String numeroConta) {
        this.numeroConta = numeroConta;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void creditar(BigDecimal valor) {
        this.saldo = this.saldo.add(valor);
    }

    public void debitar(BigDecimal valor) {
        this.saldo = this.saldo.subtract(valor);
    }

    public void cancelarConta() {
        this.situacaoConta = SituacaoConta.CANCELADA;
    }

    public SituacaoConta getSituacaoConta() {
        return situacaoConta;
    }

}
