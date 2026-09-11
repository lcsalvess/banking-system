package com.lucas.sistemabancario.dto.request;

import com.lucas.sistemabancario.entity.enums.TipoConta;
import jakarta.validation.constraints.NotNull;

public class ContaRequestDTO {

    @NotNull(message = "O ID do titular é obrigatório.")
    private Long titularId;

    @NotNull(message = "O tipo de conta é obrigatório.")
    private TipoConta tipoConta;

    public ContaRequestDTO() {}

    public ContaRequestDTO(Long titularId, TipoConta tipoConta) {
        this.titularId = titularId;
        this.tipoConta = tipoConta;
    }

    public Long getTitularId() {
        return titularId;
    }

    public void setTitularId(Long titularId) {
        this.titularId = titularId;
    }

    public TipoConta getTipoConta() {
        return tipoConta;
    }

    public void setTipoConta(TipoConta tipoConta) {
        this.tipoConta = tipoConta;
    }
}
