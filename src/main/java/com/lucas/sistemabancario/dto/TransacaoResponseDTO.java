package com.lucas.sistemabancario.dto;

import com.lucas.sistemabancario.entity.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransacaoResponseDTO {

    private Long id;
    private TipoTransacao tipoTransacao;
    private BigDecimal valor;
    private LocalDateTime dataHora;

    public TransacaoResponseDTO(Long id, TipoTransacao tipoTransacao, BigDecimal valor, LocalDateTime dataHora) {
        this.id = id;
        this.tipoTransacao = tipoTransacao;
        this.valor = valor;
        this.dataHora = dataHora;
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
}
