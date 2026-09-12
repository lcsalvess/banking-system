package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.TransacaoRequestDTO;
import com.lucas.sistemabancario.dto.response.TransacaoResponseDTO;
import com.lucas.sistemabancario.entity.Conta;
import com.lucas.sistemabancario.entity.ContaPoupanca;
import com.lucas.sistemabancario.entity.Transacao;
import com.lucas.sistemabancario.entity.enums.SituacaoConta;
import com.lucas.sistemabancario.entity.enums.TipoTransacao;
import com.lucas.sistemabancario.exception.conta.AccountIsNotActiveException;
import com.lucas.sistemabancario.exception.conta.AccountIsNotSavingsException;
import com.lucas.sistemabancario.exception.conta.AccountsAreSameException;
import com.lucas.sistemabancario.exception.transacao.InterestAlreadyAppliedException;
import com.lucas.sistemabancario.exception.transacao.InterestNotAvailableException;
import com.lucas.sistemabancario.exception.transacao.InsufficientBalanceException;
import com.lucas.sistemabancario.exception.transacao.InvalidAmountException;
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
    public TransacaoResponseDTO depositar(TransacaoRequestDTO dto) {
        Conta conta = contaService.buscarContaPorId(dto.getContaId());
        validarContaAtiva(conta);
        validarValor(dto.getValor());
        conta.creditar(dto.getValor());
        Transacao transacao = registrarTransacao(TipoTransacao.DEPOSITO, dto.getValor(), conta);
        return TransacaoResponseDTO.fromEntity(transacao);
    }

    public List<TransacaoResponseDTO> listarPorConta(Long contaId) {
        contaService.buscarContaPorId(contaId);
        return transacaoRepository.findByContaId(contaId)
                .stream()
                .map(TransacaoResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public TransacaoResponseDTO sacar(TransacaoRequestDTO dto) {
        Conta conta = contaService.buscarContaPorId(dto.getContaId());
        validarContaAtiva(conta);
        validarValor(dto.getValor());
        validarSaldo(conta, dto.getValor());
        conta.debitar(dto.getValor());
        Transacao transacao = registrarTransacao(TipoTransacao.SAQUE, dto.getValor(), conta);
        return TransacaoResponseDTO.fromEntity(transacao);
    }

    @Transactional
    public TransacaoResponseDTO transferir(TransacaoRequestDTO dto) {
        validarContasDiferentes(dto.getContaId(), dto.getContaIdDestino());
        Conta contaOrigem = contaService.buscarContaPorId(dto.getContaId());
        Conta contaDestino = contaService.buscarContaPorId(dto.getContaIdDestino());
        validarContaAtiva(contaOrigem);
        validarContaAtiva(contaDestino);
        validarValor(dto.getValor());
        validarSaldo(contaOrigem, dto.getValor());
        contaOrigem.debitar(dto.getValor());
        contaDestino.creditar(dto.getValor());
        Transacao enviada = registrarTransacao(TipoTransacao.TRANSFERENCIA_ENVIADA, dto.getValor(), contaOrigem);
        registrarTransacao(TipoTransacao.TRANSFERENCIA_RECEBIDA, dto.getValor(), contaDestino);
        return TransacaoResponseDTO.fromEntity(enviada);
    }

    @Transactional
    public TransacaoResponseDTO aplicarRendimento(Long contaId) {
        Conta conta = contaService.buscarContaPorId(contaId);
        ContaPoupanca contaPoupanca = validarEObterContaPoupanca(conta);
        validarContaAtiva(contaPoupanca);
        validarRendimentoJaAplicado(contaPoupanca.getId());
        validarRendimentoDisponivel(contaPoupanca);
        BigDecimal rendimento = contaPoupanca.calcularRendimento();
        contaPoupanca.creditar(rendimento);
        Transacao transacao = registrarTransacao(TipoTransacao.RENDIMENTO, rendimento, contaPoupanca);
        return TransacaoResponseDTO.fromEntity(transacao);
    }

    private void validarContaAtiva(Conta conta) {
        if (conta.getSituacaoConta() != SituacaoConta.ATIVA) {
            throw new AccountIsNotActiveException("A conta informada não está ativa.");
        }
    }

    private void validarValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("O valor deve ser maior que zero.");
        }
    }

    private void validarSaldo(Conta conta, BigDecimal valor) {
        if (conta.getSaldo().compareTo(valor) < 0) {
            throw new InsufficientBalanceException("O valor informado é maior do que o saldo.");
        }
    }

    private void validarContasDiferentes(Long contaIdOrigem, Long contaIdDestino) {
        if (contaIdOrigem.equals(contaIdDestino)) {
            throw new AccountsAreSameException("A conta de origem não pode ser igual à conta de destino.");
        }
    }

    private ContaPoupanca validarEObterContaPoupanca(Conta conta) {
        if (!(conta instanceof ContaPoupanca contaPoupanca)) {
            throw new AccountIsNotSavingsException("A conta informada não é poupança.");
        }
        return contaPoupanca;
    }

    private void validarRendimentoDisponivel(ContaPoupanca contaPoupanca) {
        if (!contaPoupanca.podeReceberRendimento()) {
            throw new InterestNotAvailableException("A conta ainda não está disponível para receber rendimento");
        }
    }

    private void validarRendimentoJaAplicado(Long contaId) {
        LocalDateTime inicioDoDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDoDia = LocalDate.now().atTime(LocalTime.MAX);
        boolean rendimentoJaAplicado = transacaoRepository.existsByContaIdAndTipoTransacaoAndDataHoraBetween(contaId, TipoTransacao.RENDIMENTO, inicioDoDia, fimDoDia);
        if (rendimentoJaAplicado) {
            throw new InterestAlreadyAppliedException("O rendimento já foi aplicado para a conta hoje.");
        }
    }

    private Transacao registrarTransacao(TipoTransacao tipoTransacao, BigDecimal valor, Conta conta) {
        Transacao transacao = new Transacao(tipoTransacao, valor, LocalDateTime.now(), conta);
        return transacaoRepository.save(transacao);
    }
}
