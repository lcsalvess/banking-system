package com.lucas.sistemabancario.service;

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
import com.lucas.sistemabancario.exception.transacao.ValorInvalidoException;
import com.lucas.sistemabancario.repository.TransacaoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class TransacaoService {
    private final TransacaoRepository transacaoRepository;
    private final ContaService contaService;

    public TransacaoService(TransacaoRepository transacaoRepository, ContaService contaService) {
        this.transacaoRepository = transacaoRepository;
        this.contaService = contaService;
    }

    @Transactional
    public void depositar(Long contaId, BigDecimal valor) {
        Conta conta = contaService.buscarContaPorId(contaId);
        validarContaAtiva(conta);
        validarValor(valor);
        conta.creditar(valor);
        registrarTransacao(TipoTransacao.DEPOSITO, valor, conta);
    }

    public List<TransacaoResponseDTO> listarPorConta(Long contaId) {
        contaService.buscarContaPorId(contaId);
        return transacaoRepository.findByContaId(contaId)
                .stream()
                .map(transacao -> new TransacaoResponseDTO(
                        transacao.getId(), transacao.getTipoTransacao(),
                        transacao.getValor(), transacao.getDataHora()
                ))
                .toList();
    }

    @Transactional
    public void sacar(Long contaId, BigDecimal valor) {
        Conta conta = contaService.buscarContaPorId(contaId);
        validarContaAtiva(conta);
        validarValor(valor);
        validarSaldo(conta, valor);
        conta.debitar(valor);
        registrarTransacao(TipoTransacao.SAQUE, valor, conta);
    }

    @Transactional
    public void transferir(Long contaIdOrigem, Long contaIdDestino, BigDecimal valor) {
        validarContasDiferentes(contaIdOrigem, contaIdDestino);
        Conta contaOrigem = contaService.buscarContaPorId(contaIdOrigem);
        Conta contaDestino = contaService.buscarContaPorId(contaIdDestino);
        validarContaAtiva(contaOrigem);
        validarContaAtiva(contaDestino);
        validarValor(valor);
        validarSaldo(contaOrigem, valor);
        contaOrigem.debitar(valor);
        contaDestino.creditar(valor);
        registrarTransacao(TipoTransacao.TRANSFERENCIA_ENVIADA, valor, contaOrigem);
        registrarTransacao(TipoTransacao.TRANSFERENCIA_RECEBIDA, valor, contaDestino);
    }

    @Transactional
    public void aplicarRendimento(Long contaId) {
        Conta conta = contaService.buscarContaPorId(contaId);
        ContaPoupanca contaPoupanca = validarEObterContaPoupanca(conta);
        validarContaAtiva(contaPoupanca);
        validarRendimentoJaAplicado(contaPoupanca.getId());
        validarRendimentoDisponivel(contaPoupanca);
        BigDecimal rendimento = contaPoupanca.calcularRendimento();
        contaPoupanca.creditar(rendimento);
        registrarTransacao(TipoTransacao.RENDIMENTO, rendimento, contaPoupanca);
    }

    private void validarContaAtiva(Conta conta) {
        if (conta.getSituacaoConta() != SituacaoConta.ATIVA) {
            throw new ContaIsNotActiveException("A conta informada não está ativa.");
        }
    }

    private void validarValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValorInvalidoException("O valor deve ser maior que zero.");
        }
    }

    private void validarSaldo(Conta conta, BigDecimal valor) {
        if (conta.getSaldo().compareTo(valor) < 0) {
            throw new SaldoIsNotEnoughException("O valor informado é maior do que o saldo.");
        }
    }

    private void validarContasDiferentes(Long contaIdOrigem, Long contaIdDestino) {
        if (contaIdOrigem.equals(contaIdDestino)) {
            throw new ContasIguaisException("A conta de origem não pode ser igual à conta de destino.");
        }
    }

    private ContaPoupanca validarEObterContaPoupanca(Conta conta) {
        if (!(conta instanceof ContaPoupanca contaPoupanca)) {
            throw new ContaIsNotPoupancaException("A conta informada não é poupança.");
        }
        return contaPoupanca;
    }

    private void validarRendimentoDisponivel(ContaPoupanca contaPoupanca) {
        if (!contaPoupanca.podeReceberRendimento()){
            throw new RendimentoNaoDisponivelException("A conta ainda não está disponível para receber rendimento");
        }
    }

    private void validarRendimentoJaAplicado(Long contaId) {
        LocalDateTime inicioDoDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDoDia = LocalDate.now().atTime(LocalTime.MAX);
        boolean rendimentoJaAplicado = transacaoRepository.existsByContaIdAndTipoTransacaoAndDataHoraBetween(contaId, TipoTransacao.RENDIMENTO, inicioDoDia, fimDoDia);
        if (rendimentoJaAplicado) {
            throw new RendimentoJaAplicadoException("O rendimento já foi aplicado para a conta hoje.");
        }
    }

    private void registrarTransacao(TipoTransacao tipoTransacao, BigDecimal valor, Conta conta) {
        Transacao transacao = new Transacao(tipoTransacao, valor, LocalDateTime.now(), conta);
        transacaoRepository.save(transacao);
    }
}
