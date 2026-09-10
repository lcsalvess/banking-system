package com.lucas.sistemabancario.entity;

import com.lucas.sistemabancario.entity.enums.TipoConta;
import jakarta.persistence.Entity;

@Entity
public class ContaCorrente extends Conta{
    public ContaCorrente () {}

    public ContaCorrente(Cliente titular, String numeroConta) {
        super(titular, numeroConta, TipoConta.CORRENTE);
    }
}
