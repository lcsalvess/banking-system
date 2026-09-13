package com.lucas.sistemabancario.dto.request.transacao;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class TransferenciaRequestDTO {

    @NotNull(message = "O ID da conta de origem é obrigatório.")
    private Long contaIdOrigem;

    @NotNull(message = "O ID da conta de destino é obrigatório.")
    private Long contaIdDestino;

    @NotNull(message = "O valor da transferência é obrigatório.")
    @Positive(message = "O valor da transferência deve ser maior que zero.")
    private BigDecimal valor;

    public TransferenciaRequestDTO() {}

    public Long getContaIdOrigem() {
        return contaIdOrigem;
    }

    public void setContaIdOrigem(Long contaIdOrigem) {
        this.contaIdOrigem = contaIdOrigem;
    }

    public Long getContaIdDestino() {
        return contaIdDestino;
    }

    public void setContaIdDestino(Long contaIdDestino) {
        this.contaIdDestino = contaIdDestino;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }
}
