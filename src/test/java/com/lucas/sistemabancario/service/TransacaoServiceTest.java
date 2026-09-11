package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.TransacaoRequestDTO;
import com.lucas.sistemabancario.dto.response.TransacaoResponseDTO;
import com.lucas.sistemabancario.entity.Conta;
import com.lucas.sistemabancario.entity.ContaPoupanca;
import com.lucas.sistemabancario.entity.Transacao;
import com.lucas.sistemabancario.entity.enums.SituacaoConta;
import com.lucas.sistemabancario.entity.enums.TipoTransacao;
import com.lucas.sistemabancario.exception.conta.ContaIsNotActiveException;
import com.lucas.sistemabancario.exception.conta.ContaIsNotPoupancaException;
import com.lucas.sistemabancario.exception.conta.ContasIguaisException;
import com.lucas.sistemabancario.exception.transacao.RendimentoJaAplicadoException;
import com.lucas.sistemabancario.exception.transacao.RendimentoNaoDisponivelException;
import com.lucas.sistemabancario.exception.transacao.SaldoIsNotEnoughException;
import com.lucas.sistemabancario.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
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

    private TransacaoRequestDTO criarDto(Long contaId, TipoTransacao tipo, BigDecimal valor) {
        TransacaoRequestDTO dto = new TransacaoRequestDTO();
        dto.setContaId(contaId);
        dto.setTipoTransacao(tipo);
        dto.setValor(valor);
        return dto;
    }

    @Nested
    @DisplayName("Testes de depósito")
    class DepositoTests {
        private final Long contaId = 1L;
        private Conta conta;

        @BeforeEach
        void setUp() {
            conta = new Conta() {};
            ReflectionTestUtils.setField(conta, "id", contaId);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar depositar em uma conta CANCELADA.")
        void deveLancarExcecaoQuandoContaNaoEstaAtiva() {
            ReflectionTestUtils.setField(conta, "situacaoConta", SituacaoConta.CANCELADA);
            when(contaService.buscarContaPorId(contaId)).thenReturn(conta);

            TransacaoRequestDTO dto = criarDto(contaId, TipoTransacao.DEPOSITO, BigDecimal.TEN);

            assertThrows(ContaIsNotActiveException.class, () -> transacaoService.depositar(dto));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar depósito em uma conta ATIVA e valor VÁLIDO.")
        void deveRealizarDepositoQuandoContaEstaAtivaEValorValido() {
            when(contaService.buscarContaPorId(contaId)).thenReturn(conta);
            when(transacaoRepository.save(any(Transacao.class))).thenAnswer(i -> i.getArgument(0));

            TransacaoRequestDTO dto = criarDto(contaId, TipoTransacao.DEPOSITO, BigDecimal.TEN);

            TransacaoResponseDTO response = transacaoService.depositar(dto);

            assertEquals(BigDecimal.TEN, conta.getSaldo());
            assertNotNull(response);
            assertEquals(BigDecimal.TEN, response.getValor());
            assertEquals(TipoTransacao.DEPOSITO, response.getTipoTransacao());
            verify(transacaoRepository, times(1)).save(any(Transacao.class));
        }
    }

    @Nested
    @DisplayName("Testes de saque")
    class SaqueTests {
        private final Long contaId = 1L;
        private Conta conta;

        @BeforeEach
        void setUp() {
            conta = new Conta() {};
            ReflectionTestUtils.setField(conta, "id", contaId);
            ReflectionTestUtils.setField(conta, "saldo", new BigDecimal("100.00"));
            when(contaService.buscarContaPorId(contaId)).thenReturn(conta);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar sacar de uma conta CANCELADA.")
        void deveLancarExcecaoQuandoContaNaoEstaAtiva() {
            ReflectionTestUtils.setField(conta, "situacaoConta", SituacaoConta.CANCELADA);
            TransacaoRequestDTO dto = criarDto(contaId, TipoTransacao.SAQUE, BigDecimal.TEN);

            assertThrows(ContaIsNotActiveException.class, () -> transacaoService.sacar(dto));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar sacar mais do que tem em conta.")
        void deveLancarExcecaoQuandoSaqueEhMaiorQueSaldo() {
            TransacaoRequestDTO dto = criarDto(contaId, TipoTransacao.SAQUE, new BigDecimal("200.00"));

            assertThrows(SaldoIsNotEnoughException.class, () -> transacaoService.sacar(dto));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve retornar sucesso ao tentar sacar o mesmo valor do saldo.")
        void deveRealizarSaqueQuandoValorSaqueIgualASaldo() {
            when(transacaoRepository.save(any(Transacao.class))).thenAnswer(i -> i.getArgument(0));
            TransacaoRequestDTO dto = criarDto(contaId, TipoTransacao.SAQUE, new BigDecimal("100.00"));

            transacaoService.sacar(dto);

            assertEquals(new BigDecimal("0.00"), conta.getSaldo());
            verify(transacaoRepository, times(1)).save(any(Transacao.class));
        }

        @Test
        @DisplayName("Deve retornar sucesso ao tentar sacar valor menor que o saldo.")
        void deveRealizarSaqueQuandoValorMenorQueSaldo() {
            when(transacaoRepository.save(any(Transacao.class))).thenAnswer(i -> i.getArgument(0));
            TransacaoRequestDTO dto = criarDto(contaId, TipoTransacao.SAQUE, new BigDecimal("30.00"));

            transacaoService.sacar(dto);

            assertEquals(new BigDecimal("70.00"), conta.getSaldo());
            verify(transacaoRepository, times(1)).save(any(Transacao.class));
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
            contaOrigem = new Conta() {};
            contaDestino = new Conta() {};
            ReflectionTestUtils.setField(contaOrigem, "id", contaIdOrigem);
            ReflectionTestUtils.setField(contaOrigem, "saldo", new BigDecimal("100.00"));
            ReflectionTestUtils.setField(contaDestino, "id", contaIdDestino);
            ReflectionTestUtils.setField(contaDestino, "saldo", new BigDecimal("50.00"));
        }

        private TransacaoRequestDTO criarDtoTransferencia(BigDecimal valor) {
            TransacaoRequestDTO dto = criarDto(contaIdOrigem, TipoTransacao.TRANSFERENCIA_ENVIADA, valor);
            dto.setContaIdDestino(contaIdDestino);
            return dto;
        }

        @Test
        @DisplayName("Deve lançar exceção se a transferência for entre a mesma conta")
        void deveLancarExcecaoTransferenciaEntreMesmaConta() {
            TransacaoRequestDTO dto = criarDto(contaIdOrigem, TipoTransacao.TRANSFERENCIA_ENVIADA, BigDecimal.TEN);
            dto.setContaIdDestino(contaIdOrigem); // Forçando IDs iguais

            assertThrows(ContasIguaisException.class, () -> transacaoService.transferir(dto));
            verify(contaService, never()).buscarContaPorId(any());
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção se a conta ORIGEM está cancelada.")
        void deveLancarExcecaoCasoContaOrigemEstaCancelada() {
            mockarBuscaDeContas();
            ReflectionTestUtils.setField(contaOrigem, "situacaoConta", SituacaoConta.CANCELADA);
            TransacaoRequestDTO dto = criarDtoTransferencia(BigDecimal.TEN);

            assertThrows(ContaIsNotActiveException.class, () -> transacaoService.transferir(dto));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção se a conta DESTINO está cancelada.")
        void deveLancarExcecaoCasoContaDestinoEstaCancelada() {
            mockarBuscaDeContas();
            ReflectionTestUtils.setField(contaDestino, "situacaoConta", SituacaoConta.CANCELADA);
            TransacaoRequestDTO dto = criarDtoTransferencia(BigDecimal.TEN);

            assertThrows(ContaIsNotActiveException.class, () -> transacaoService.transferir(dto));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar transferir valor maior que o saldo da conta origem")
        void deveLancarExcecaoQuandoTransferenciaEhMaiorQueSaldo() {
            mockarBuscaDeContas();
            TransacaoRequestDTO dto = criarDtoTransferencia(new BigDecimal("200.00"));

            assertThrows(SaldoIsNotEnoughException.class, () -> transacaoService.transferir(dto));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve realizar transferência com sucesso, alterar dados e salvar duas transações.")
        void deveRealizarTransferenciaComSucesso() {
            mockarBuscaDeContas();
            when(transacaoRepository.save(any(Transacao.class))).thenAnswer(i -> i.getArgument(0));

            BigDecimal valorTransferencia = new BigDecimal("30.00");
            TransacaoRequestDTO dto = criarDtoTransferencia(valorTransferencia);

            transacaoService.transferir(dto);

            assertEquals(new BigDecimal("70.00"), contaOrigem.getSaldo());
            assertEquals(new BigDecimal("80.00"), contaDestino.getSaldo());
            verify(transacaoRepository, times(2)).save(any(Transacao.class));
        }

        private void mockarBuscaDeContas() {
            when(contaService.buscarContaPorId(contaIdOrigem)).thenReturn(contaOrigem);
            when(contaService.buscarContaPorId(contaIdDestino)).thenReturn(contaDestino);
        }
    }

    @Nested
    @DisplayName("Testes de rendimento")
    class RendimentoTests {
        private final Long contaId = 1L;
        private ContaPoupanca contaPoupanca;

        @BeforeEach
        void setUp() {
            contaPoupanca = spy(new ContaPoupanca());
            ReflectionTestUtils.setField(contaPoupanca, "id", contaId);
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar aplicar rendimento em uma conta que não é poupança.")
        void deveLancarExcecaoQuandoContaNaoEhPoupanca() {
            Conta contaInvalida = new Conta() {};
            ReflectionTestUtils.setField(contaInvalida, "id", contaId);
            when(contaService.buscarContaPorId(contaId)).thenReturn(contaInvalida);

            assertThrows(ContaIsNotPoupancaException.class, () -> transacaoService.aplicarRendimento(contaId));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar aplicar rendimento já aplicado no dia")
        void deveLancarExcecaoQuandoRendimentoJaAplicadoNoDia() {
            mockarBuscaContaPoupanca();
            when(transacaoRepository.existsByContaIdAndTipoTransacaoAndDataHoraBetween(eq(contaId), any(), any(), any())).thenReturn(true);

            assertThrows(RendimentoJaAplicadoException.class, () -> transacaoService.aplicarRendimento(contaId));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção quando conta poupança estiver cancelada.")
        void deveLancarExcecaoQuandoContaPoupancaEstiverCancelada() {
            ReflectionTestUtils.setField(contaPoupanca, "situacaoConta", SituacaoConta.CANCELADA);
            mockarBuscaContaPoupanca();

            assertThrows(ContaIsNotActiveException.class, () -> transacaoService.aplicarRendimento(contaId));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exceção quando a conta poupança não puder receber rendimento.")
        void deveLancarExcecaoQuandoContaNaoPuderReceberRendimento() {
            mockarBuscaContaPoupanca();
            doReturn(false).when(contaPoupanca).podeReceberRendimento();

            assertThrows(RendimentoNaoDisponivelException.class, () -> transacaoService.aplicarRendimento(contaId));
            verify(transacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve aplicar rendimento com sucesso quando ainda não foi aplicado hoje.")
        void deveAplicarRendimentoComSucesso() {
            mockarBuscaContaPoupanca();
            when(transacaoRepository.save(any(Transacao.class))).thenAnswer(i -> i.getArgument(0));

            BigDecimal valorRendimento = new BigDecimal("15.50");
            doReturn(true).when(contaPoupanca).podeReceberRendimento();
            doReturn(valorRendimento).when(contaPoupanca).calcularRendimento();
            when(transacaoRepository.existsByContaIdAndTipoTransacaoAndDataHoraBetween(eq(contaId), any(), any(), any()))
                    .thenReturn(false);

            transacaoService.aplicarRendimento(contaId);

            assertEquals(valorRendimento, contaPoupanca.getSaldo());
            verify(transacaoRepository).save(any(Transacao.class));
        }

        private void mockarBuscaContaPoupanca() {
            when(contaService.buscarContaPorId(contaId)).thenReturn(contaPoupanca);
        }
    }
}