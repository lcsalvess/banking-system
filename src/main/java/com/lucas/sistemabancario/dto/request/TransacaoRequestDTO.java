package com.lucas.sistemabancario.dto.request;

import com.lucas.sistemabancario.entity.enums.TipoTransacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class TransacaoRequestDTO {
    @NotNull(message = "O ID da conta é obrigatório.")
    private Long contaId;

    private Long contaIdDestino;

    @NotNull(message = "O tipo da transação é obrigatório.")
    private TipoTransacao tipoTransacao;

    @NotNull(message = "O valor da transação é obrigatório.")
    @Positive(message = "O valor da transação deve ser maior que zero.")
    private BigDecimal valor;

    public TransacaoRequestDTO () {}

    public Long getContaId() {
        return contaId;
    }

    public void setContaId(Long contaId) {
        this.contaId = contaId;
    }

    public Long getContaIdDestino() {
        return contaIdDestino;
    }

    public void setContaIdDestino(Long contaIdDestino) {
        this.contaIdDestino = contaIdDestino;
    }

    public TipoTransacao getTipoTransacao() {
        return tipoTransacao;
    }

    public void setTipoTransacao(TipoTransacao tipoTransacao) {
        this.tipoTransacao = tipoTransacao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }
}
