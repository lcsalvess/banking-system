package com.lucas.sistemabancario.dto.response;

import com.lucas.sistemabancario.entity.Conta;
import com.lucas.sistemabancario.entity.enums.SituacaoConta;
import com.lucas.sistemabancario.entity.enums.TipoConta;

import java.math.BigDecimal;

public class ContaResponseDTO {

    private Long id;
    private String numeroConta;
    private String nomeTitular;
    private BigDecimal saldo;
    private TipoConta tipoConta;
    private SituacaoConta situacaoConta;

    public ContaResponseDTO(Long id, String numeroConta, String nomeTitular, BigDecimal saldo, TipoConta tipoConta, SituacaoConta situacaoConta) {
        this.id = id;
        this.numeroConta = numeroConta;
        this.nomeTitular = nomeTitular;
        this.saldo = saldo;
        this.tipoConta = tipoConta;
        this.situacaoConta = situacaoConta;
    }

    public Long getId() {
        return id;
    }

    public String getNumeroConta() {
        return numeroConta;
    }

    public String getNomeTitular() {
        return nomeTitular;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public TipoConta getTipoConta() {
        return tipoConta;
    }

    public SituacaoConta getSituacaoConta() {
        return situacaoConta;
    }

    public static ContaResponseDTO fromEntity(Conta conta) {
        return new ContaResponseDTO(
                conta.getId(),
                conta.getNumeroConta(),
                conta.getTitular().getNome(),
                conta.getSaldo(),
                conta.getTipoConta(),
                conta.getSituacaoConta()
        );
    }


}
