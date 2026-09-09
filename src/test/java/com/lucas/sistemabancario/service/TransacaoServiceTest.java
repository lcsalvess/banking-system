package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.entity.Conta;
import com.lucas.sistemabancario.entity.ContaPoupanca;
import com.lucas.sistemabancario.entity.Transacao;
import com.lucas.sistemabancario.entity.enums.SituacaoConta;
import com.lucas.sistemabancario.exception.ContaIsNotActiveException;
import com.lucas.sistemabancario.exception.RendimentoJaAplicadoException;
import com.lucas.sistemabancario.exception.ValorInvalidoException;
import com.lucas.sistemabancario.repository.TransacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("Testes de depósito")
    class DepositoTests {
        @Test
        @DisplayName("Deve lançar exceção ao tentar depositar em uma conta CANCELADA.")
        void deveLancarExcecaoQuandoContaNaoEstaAtiva() {
            Long contaId = 1L;
            Conta conta = mock(Conta.class);
            when(conta.getSituacaoConta())
                    .thenReturn(SituacaoConta.CANCELADA);
            when(contaService.buscarContaPorId(contaId))
                    .thenReturn(conta);
            assertThrows(ContaIsNotActiveException.class, () -> transacaoService.depositar(contaId, BigDecimal.TEN));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar depósito em uma conta ATIVA e valor VÁLIDO.")
        void deveRealizarDepositoQuandoContaEstaAtivaEValorValido() {
            Long contaId = 1L;
            Conta conta = mock(Conta.class);
            when(conta.getSituacaoConta())
                    .thenReturn(SituacaoConta.ATIVA);
            when(contaService.buscarContaPorId(contaId))
                    .thenReturn(conta);
            transacaoService.depositar(contaId, BigDecimal.TEN);
            verify(transacaoRepository).save(any(Transacao.class));
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar depositar com valor null.")
        void deveLancarExcecaoQuandoValorEhNull() {
            Long contaId = 1L;
            Conta conta = mock(Conta.class);
            when(conta.getSituacaoConta())
                    .thenReturn(SituacaoConta.ATIVA);
            when(contaService.buscarContaPorId(contaId))
                    .thenReturn(conta);
            assertThrows(ValorInvalidoException.class, () -> transacaoService.depositar(contaId, null));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar depositar com valor zero.")
        void deveLancarExcecaoQuandoValorEhZero() {
            Long contaId = 1L;
            Conta conta = mock(Conta.class);
            when(conta.getSituacaoConta())
                    .thenReturn(SituacaoConta.ATIVA);
            when(contaService.buscarContaPorId(contaId))
                    .thenReturn(conta);
            assertThrows(ValorInvalidoException.class, () -> transacaoService.depositar(contaId, BigDecimal.ZERO));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar depositar com valor negativo.")
        void deveLancarExcecaoQuandoValorEhNegativo() {
            Long contaId = 1L;
            Conta conta = mock(Conta.class);
            when(conta.getSituacaoConta())
                    .thenReturn(SituacaoConta.ATIVA);
            when(contaService.buscarContaPorId(contaId))
                    .thenReturn(conta);
            assertThrows(ValorInvalidoException.class, () -> transacaoService.depositar(contaId, new BigDecimal("-10.00")));
            verify(transacaoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Testes de rendimento")
    class RendimentoTests {
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
            assertThrows(RendimentoJaAplicadoException.class, () -> transacaoService.aplicarRendimento(contaId));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve aplicar rendimento com sucesso quando ainda não foi aplicado hoje")
        void deveAplicarRendimentoComSucesso() {
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
}
