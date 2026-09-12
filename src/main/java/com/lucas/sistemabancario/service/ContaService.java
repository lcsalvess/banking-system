package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.ContaRequestDTO;
import com.lucas.sistemabancario.dto.response.ContaResponseDTO;
import com.lucas.sistemabancario.entity.Cliente;
import com.lucas.sistemabancario.entity.Conta;
import com.lucas.sistemabancario.entity.ContaCorrente;
import com.lucas.sistemabancario.entity.ContaPoupanca;
import com.lucas.sistemabancario.entity.enums.SituacaoConta;
import com.lucas.sistemabancario.entity.enums.TipoConta;
import com.lucas.sistemabancario.exception.conta.*;
import com.lucas.sistemabancario.repository.ContaCorrenteRepository;
import com.lucas.sistemabancario.repository.ContaPoupancaRepository;
import com.lucas.sistemabancario.repository.ContaRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ContaService {
    private final ContaRepository contaRepository;
    private final ContaCorrenteRepository contaCorrenteRepository;
    private final ContaPoupancaRepository contaPoupancaRepository;
    private final ClienteService clienteService;

    public ContaService(ContaRepository contaRepository, ContaCorrenteRepository contaCorrenteRepository, ContaPoupancaRepository contaPoupancaRepository, ClienteService clienteService) {
        this.contaRepository = contaRepository;
        this.contaCorrenteRepository = contaCorrenteRepository;
        this.contaPoupancaRepository = contaPoupancaRepository;
        this.clienteService = clienteService;
    }

    public List<ContaResponseDTO> listar() {
        return contaRepository.findAll()
                .stream()
                .map(ContaResponseDTO::fromEntity)
                .toList();
    }

    public Conta buscarContaPorId(Long id) {
        return contaRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada"));
    }

    public ContaResponseDTO buscarPorId(Long id) {
        Conta conta = buscarContaPorId(id);
        return ContaResponseDTO.fromEntity(conta);
    }

    private String gerarNumeroConta() {
        Long proximoNumero = contaRepository.gerarProximoNumeroConta();
        String numeroBase = String.format("%05d", proximoNumero);
        int digito = calcularDigitoVerificador(numeroBase);
        return numeroBase + digito;
    }

    private int calcularDigitoVerificador(String numeroBase) {
        int soma = 0;
        int[] pesos = {5, 4, 3, 2, 1};
        for (int i = 0; i < numeroBase.length(); i++) {
            int digito = Character.getNumericValue(numeroBase.charAt(i));
            soma += digito * pesos[i];
        }
        return soma % 10;
    }

    @Transactional
    public ContaResponseDTO criar(ContaRequestDTO dto) {
        Cliente cliente = clienteService.buscarClientePorId(dto.getTitularId());
        Conta conta;
        if (dto.getTipoConta() == TipoConta.CORRENTE) {
            if (contaCorrenteRepository.existsByTitularId(dto.getTitularId())) {
                throw new AccountAlreadyExistsException("O cliente já possui uma conta corrente");
            }
            String numeroConta = gerarNumeroConta();
            conta = new ContaCorrente(cliente, numeroConta);
        } else if (dto.getTipoConta() == TipoConta.POUPANCA) {
            if (contaPoupancaRepository.existsByTitularId(dto.getTitularId())) {
                throw new AccountAlreadyExistsException("O cliente já possui uma conta poupança");
            }
            String numeroConta = gerarNumeroConta();
            LocalDate dataUltimoRendimento = LocalDate.now();
            conta = new ContaPoupanca(cliente, numeroConta, dataUltimoRendimento);
        } else {
            throw new InvalidAccountTypeException("Tipo de conta inválido.");
        }
        Conta contaSalva = contaRepository.save(conta);

        return ContaResponseDTO.fromEntity(contaSalva);
    }

    @Transactional
    public void cancelarConta(Long contaId) {
        Conta conta = contaRepository.findById(contaId)
                .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada."));
        validarContaAtiva(conta);
        validarSeContaTemSaldo(conta);
        conta.cancelarConta();
    }

    private void validarSeContaTemSaldo(Conta conta) {
        if (conta.getSaldo().compareTo(BigDecimal.ZERO) != 0) {
            throw new AccountHasBalanceException("Não é possível cancelar uma conta com saldo.");
        }
    }

    private void validarContaAtiva(Conta conta) {
        if (conta.getSituacaoConta() != SituacaoConta.ATIVA) {
            throw new AccountIsNotActiveException("Não é possível cancelar uma conta que não está ativa.");
        }
    }
}
