package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.entity.Conta;
import com.lucas.sistemabancario.entity.ContaPoupanca;
import com.lucas.sistemabancario.entity.Transacao;
import com.lucas.sistemabancario.entity.enums.SituacaoConta;
import com.lucas.sistemabancario.exception.ContaIsNotActiveException;
import com.lucas.sistemabancario.exception.RendimentoJaAplicadoException;
import com.lucas.sistemabancario.exception.ValorInvalidoException;
import com.lucas.sistemabancario.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
        private final Long contaId = 1L;
        private Conta conta;

        @BeforeEach
        void setUp() {
            conta = new Conta() {
            };
            ReflectionTestUtils.setField(conta, "id", contaId);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar depositar em uma conta CANCELADA.")
        void deveLancarExcecaoQuandoContaNaoEstaAtiva() {
            ReflectionTestUtils.setField(conta, "situacaoConta", SituacaoConta.CANCELADA);
            when(contaService.buscarContaPorId(contaId)).thenReturn(conta);
            assertThrows(ContaIsNotActiveException.class, () -> transacaoService.depositar(contaId, BigDecimal.TEN));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar depósito em uma conta ATIVA e valor VÁLIDO.")
        void deveRealizarDepositoQuandoContaEstaAtivaEValorValido() {
            when(contaService.buscarContaPorId(contaId)).thenReturn(conta);
            transacaoService.depositar(contaId, BigDecimal.TEN);
            verify(transacaoRepository).save(any(Transacao.class));
        }

        @ParameterizedTest
        @DisplayName("Deve lançar exceção ao tentar depositar com valores inválidos (null, zero ou negativo).")
        @NullSource
        @ValueSource(strings = {"0.00", "-0.01", "-10.00"})
        void deveLancarExcecaoQuandoValorEhInvalido(BigDecimal valorInvalido) {
            when(contaService.buscarContaPorId(contaId)).thenReturn(conta);
            assertThrows(ValorInvalidoException.class, () -> transacaoService.depositar(contaId, valorInvalido));
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
            when(contaPoupanca.getId()).thenReturn(contaId);
            when(contaPoupanca.getSituacaoConta()).thenReturn(SituacaoConta.ATIVA);
            when(contaService.buscarContaPorId(contaId)).thenReturn(contaPoupanca);
            when(transacaoRepository.existsByContaIdAndTipoTransacaoAndDataHoraBetween(eq(contaId), any(), any(), any())).thenReturn(true);
            assertThrows(RendimentoJaAplicadoException.class, () -> transacaoService.aplicarRendimento(contaId));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve aplicar rendimento com sucesso quando ainda não foi aplicado hoje")
        void deveAplicarRendimentoComSucesso() {
            Long contaId = 1L;
            ContaPoupanca contaPoupanca = mock(ContaPoupanca.class);
            when(contaPoupanca.getId()).thenReturn(contaId);
            when(contaPoupanca.getSituacaoConta()).thenReturn(SituacaoConta.ATIVA);
            when(contaPoupanca.podeReceberRendimento()).thenReturn(true);
            when(contaPoupanca.calcularRendimento()).thenReturn(new BigDecimal("15.50"));
            when(contaService.buscarContaPorId(contaId)).thenReturn(contaPoupanca);
            when(transacaoRepository.existsByContaIdAndTipoTransacaoAndDataHoraBetween(eq(contaId), any(), any(), any())).thenReturn(false);
            transacaoService.aplicarRendimento(contaId);
            verify(contaPoupanca).creditar(new BigDecimal("15.50"));
            verify(transacaoRepository, times(1)).save(any());
        }
    }
}
