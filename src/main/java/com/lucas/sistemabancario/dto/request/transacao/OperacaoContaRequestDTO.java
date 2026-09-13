package com.lucas.sistemabancario.dto.request.transacao;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class OperacaoContaRequestDTO {

    @NotNull(message = "O ID da conta é obrigatório.")
    private Long contaId;

    @NotNull(message = "O valor da operação é obrigatório.")
    @Positive(message = "O valor da operação deve ser maior que zero.")
    private BigDecimal valor;

    public OperacaoContaRequestDTO() {}

    public Long getContaId() {
        return contaId;
    }

    public void setContaId(Long contaId) {
        this.contaId = contaId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }
}
