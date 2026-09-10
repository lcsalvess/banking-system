package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.entity.Conta;
import com.lucas.sistemabancario.entity.ContaPoupanca;
import com.lucas.sistemabancario.entity.Transacao;
import com.lucas.sistemabancario.entity.enums.SituacaoConta;
import com.lucas.sistemabancario.entity.enums.TipoTransacao;
import com.lucas.sistemabancario.exception.*;
import com.lucas.sistemabancario.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        @ParameterizedTest
        @DisplayName("Deve lançar exceção ao tentar depositar com valores inválidos (null, zero ou negativo).")
        @NullSource
        @ValueSource(strings = {"0.00", "-0.01", "-10.00"})
        void deveLancarExcecaoQuandoValorEhInvalido(BigDecimal valorInvalido) {
            when(contaService.buscarContaPorId(contaId)).thenReturn(conta);
            assertThrows(ValorInvalidoException.class, () -> transacaoService.depositar(contaId, valorInvalido));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar depósito em uma conta ATIVA e valor VÁLIDO.")
        void deveRealizarDepositoQuandoContaEstaAtivaEValorValido() {
            ArgumentCaptor<Transacao> transacaoCaptor = ArgumentCaptor.forClass(Transacao.class);
            when(contaService.buscarContaPorId(contaId)).thenReturn(conta);
            transacaoService.depositar(contaId, BigDecimal.TEN);
            assertEquals(BigDecimal.TEN, conta.getSaldo());
            verify(transacaoRepository).save(transacaoCaptor.capture());
            Transacao transacao = transacaoCaptor.getValue();
            assertEquals(TipoTransacao.DEPOSITO, transacao.getTipoTransacao());
            assertEquals(BigDecimal.TEN, transacao.getValor());
            assertEquals(conta, transacao.getConta());
        }
    }

    @Nested
    @DisplayName("Testes de saque")
    class SaqueTests {
        private final Long contaId = 1L;
        private Conta conta;

        @BeforeEach
        void setUp() {
            conta = new Conta() {
            };
            ReflectionTestUtils.setField(conta, "id", contaId);
            ReflectionTestUtils.setField(conta, "saldo", new BigDecimal("100.00"));
            when(contaService.buscarContaPorId(contaId)).thenReturn(conta);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar sacar de uma conta CANCELADA.")
        void deveLancarExcecaoQuandoContaNaoEstaAtiva() {
            ReflectionTestUtils.setField(conta, "situacaoConta", SituacaoConta.CANCELADA);
            assertThrows(ContaIsNotActiveException.class, () -> transacaoService.sacar(contaId, BigDecimal.TEN));
            verify(transacaoRepository, never()).save(any());
        }

        @ParameterizedTest
        @DisplayName("Deve lançar exceção ao tentar sacar com valores inválidos (null, zero ou negativo).")
        @NullSource
        @ValueSource(strings = {"0.00", "-0.01", "-10.00"})
        void deveLancarExcecaoQuandoValorEhInvalido(BigDecimal valorInvalido) {
            assertThrows(ValorInvalidoException.class, () -> transacaoService.sacar(contaId, valorInvalido));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar sacar mais do que tem em conta.")
        void deveLancarExcecaoQuandoSaqueEhMaiorQueSaldo() {
            assertThrows(SaldoIsNotEnoughException.class, () -> transacaoService.sacar(contaId, new BigDecimal("200.00")));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve retornar sucesso ao tentar sacar o mesmo valor do saldo.")
        void deveRealizarSaqueQuandoValorSaqueIgualASaldo() {
            ArgumentCaptor<Transacao> transacaoCaptor = ArgumentCaptor.forClass(Transacao.class);
            transacaoService.sacar(contaId, new BigDecimal("100.00"));
            assertEquals(new BigDecimal("0.00"), conta.getSaldo());
            verify(transacaoRepository).save(transacaoCaptor.capture());
            Transacao transacao = transacaoCaptor.getValue();
            assertEquals(TipoTransacao.SAQUE, transacao.getTipoTransacao());
            assertEquals(new BigDecimal("100.00"), transacao.getValor());
            assertEquals(conta, transacao.getConta());
        }

        @Test
        @DisplayName("Deve retornar sucesso ao tentar sacar valor menor que o saldo.")
        void deveRealizarSaqueQuandoValorMenorQueSaldo() {
            ArgumentCaptor<Transacao> transacaoCaptor = ArgumentCaptor.forClass(Transacao.class);
            transacaoService.sacar(contaId, new BigDecimal("30.00"));
            assertEquals(new BigDecimal("70.00"), conta.getSaldo());
            verify(transacaoRepository).save(transacaoCaptor.capture());
            Transacao transacao = transacaoCaptor.getValue();
            assertEquals(TipoTransacao.SAQUE, transacao.getTipoTransacao());
            assertEquals(new BigDecimal("30.00"), transacao.getValor());
            assertEquals(conta, transacao.getConta());
        }
    }

    @Nested
    @DisplayName("Testes de transferências")
    class TransferenciaTests {
        private final Long contaIdOrigem = 1L;
        private final Long contaIdDestino = 2L;
        private Conta contaOrigem;
        private Conta contaDestino;

        @BeforeEach
        void setUp() {
            contaOrigem = new Conta() {
            };
            contaDestino = new Conta() {
            };
            ReflectionTestUtils.setField(contaOrigem, "id", contaIdOrigem);
            ReflectionTestUtils.setField(contaOrigem, "saldo", new BigDecimal("100.00"));
            ReflectionTestUtils.setField(contaDestino, "id", contaIdDestino);
            ReflectionTestUtils.setField(contaDestino, "saldo", new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("Deve lançar exceção se a transferência for entre a mesma conta")
        void deveLancarExcecaoTransferenciaEntreMesmaConta() {
            assertThrows(ContasIguaisException.class, () -> transacaoService.transferir(contaIdOrigem, contaIdOrigem, BigDecimal.TEN));
            verify(contaService, never()).buscarContaPorId(any());
            verify(transacaoRepository, never()).save(any());
        }

        @ParameterizedTest
        @DisplayName("Deve lançar exceção ao tentar transferir com valores INVÁLIDOS (null, zero ou negativo).")
        @NullSource
        @ValueSource(strings = {"0.00", "-0.01", "-10.00"})
        void deveLancarExcecaoTransferenciaValorInvalido(BigDecimal valorInvalido) {
            mockarBuscaDeContas();
            assertThrows(ValorInvalidoException.class, () -> transacaoService.transferir(contaIdOrigem, contaIdDestino, valorInvalido));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção se a conta ORIGEM está cancelada.")
        void deveLancarExcecaoCasoContaOrigemEstaCancelada() {
            mockarBuscaDeContas();
            ReflectionTestUtils.setField(contaOrigem, "situacaoConta", SituacaoConta.CANCELADA);
            assertThrows(ContaIsNotActiveException.class, () -> transacaoService.transferir(contaIdOrigem, contaIdDestino, BigDecimal.TEN));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção se a conta DESTINO está cancelada.")
        void deveLancarExcecaoCasoContaDestinoEstaCancelada() {
            mockarBuscaDeContas();
            ReflectionTestUtils.setField(contaDestino, "situacaoConta", SituacaoConta.CANCELADA);
            assertThrows(ContaIsNotActiveException.class, () -> transacaoService.transferir(contaIdOrigem, contaIdDestino, BigDecimal.TEN));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar transferir valor maior que o saldo da conta origem")
        void deveLancarExcecaoQuandoTransferenciaEhMaiorQueSaldo() {
            mockarBuscaDeContas();
            assertThrows(SaldoIsNotEnoughException.class, () -> transacaoService.transferir(contaIdOrigem, contaIdDestino, new BigDecimal("200.00")));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar transferência com sucesso, alterar dados e salvar duas transações.")
        void deveRealizarTransferenciaComSucesso() {
            mockarBuscaDeContas();
            ArgumentCaptor<Transacao> transacaoCaptor = ArgumentCaptor.forClass(Transacao.class);
            BigDecimal valorTransferencia = new BigDecimal("30.00");
            transacaoService.transferir(contaIdOrigem, contaIdDestino, valorTransferencia);
            assertEquals(new BigDecimal("70.00"), contaOrigem.getSaldo());
            assertEquals(new BigDecimal("80.00"), contaDestino.getSaldo());
            verify(transacaoRepository, times(2)).save(transacaoCaptor.capture());
            List<Transacao> transacoesSalvas = transacaoCaptor.getAllValues();
            Transacao transacaoSaida = transacoesSalvas.get(0);
            assertEquals(contaOrigem, transacaoSaida.getConta());
            assertEquals(valorTransferencia, transacaoSaida.getValor());
            assertEquals(TipoTransacao.TRANSFERENCIA_ENVIADA, transacaoSaida.getTipoTransacao());
            Transacao transacaoEntrada = transacoesSalvas.get(1);
            assertEquals(contaDestino, transacaoEntrada.getConta());
            assertEquals(valorTransferencia, transacaoEntrada.getValor());
            assertEquals(TipoTransacao.TRANSFERENCIA_RECEBIDA, transacaoEntrada.getTipoTransacao());
        }

        private void mockarBuscaDeContas() {
            when(contaService.buscarContaPorId(contaIdOrigem)).thenReturn(contaOrigem);
            when(contaService.buscarContaPorId(contaIdDestino)).thenReturn(contaDestino);
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