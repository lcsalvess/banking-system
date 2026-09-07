package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.entity.ContaPoupanca;
import com.lucas.sistemabancario.entity.enums.SituacaoConta;
import com.lucas.sistemabancario.exception.RendimentoJaAplicadoException;
import com.lucas.sistemabancario.repository.TransacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransacaoServiceTest {
    @Mock
    private TransacaoRepository transacaoRepository;
    @Mock
    private ContaService contaService;
    @InjectMocks
    private TransacaoService transacaoService;

    @Test
    @DisplayName("Deve lançar exceção ao tentar aplicar rendimento já aplicado no dia")
    void deveLancarExcecaoQuandoRendimentoJaAplicadoNoDia() {
        Long contaId = 1L;
        ContaPoupanca contaPoupanca = mock(ContaPoupanca.class);
        when(contaPoupanca.getId())
                .thenReturn(contaId);
        when(contaPoupanca.getSituacaoConta())
                .thenReturn(SituacaoConta.ATIVA);
        when(contaService.buscarContaPorId(contaId))
                .thenReturn(contaPoupanca);
        when(transacaoRepository.existsByContaIdAndTipoTransacaoAndDataHoraBetween(eq(contaId), any(), any(), any()))
                .thenReturn(true);
        assertThrows(RendimentoJaAplicadoException.class, () -> {
            transacaoService.aplicarRendimento(contaId);
        });
        verify(transacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve aplicar rendimento com sucesso quando ainda não foi aplicado hoje")
    void deveAplicarRendimentoComSucesso(){
        Long contaId = 1L;
        ContaPoupanca contaPoupanca = mock(ContaPoupanca.class);
        when(contaPoupanca.getId())
                .thenReturn(contaId);
        when(contaPoupanca.getSituacaoConta())
                .thenReturn(SituacaoConta.ATIVA);
        when(contaPoupanca.podeReceberRendimento())
                .thenReturn(true);
        when(contaPoupanca.calcularRendimento())
                .thenReturn(new BigDecimal("15.50"));
        when(contaService.buscarContaPorId(contaId))
                .thenReturn(contaPoupanca);
        when(transacaoRepository.existsByContaIdAndTipoTransacaoAndDataHoraBetween(
                eq(contaId), any(), any(), any()
        )).thenReturn(false);
        transacaoService.aplicarRendimento(contaId);
        verify(contaPoupanca).creditar(new BigDecimal("15.50"));
        verify(transacaoRepository, times(1)).save(any());
    }
}
