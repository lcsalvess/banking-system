package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.AccountRequestDTO;
import com.lucas.sistemabancario.dto.response.AccountResponseDTO;
import com.lucas.sistemabancario.entity.Account;
import com.lucas.sistemabancario.entity.CheckingAccount;
import com.lucas.sistemabancario.entity.Client;
import com.lucas.sistemabancario.entity.SavingsAccount;
import com.lucas.sistemabancario.entity.enums.AccountStatus;
import com.lucas.sistemabancario.entity.enums.AccountType;
import com.lucas.sistemabancario.exception.account.*;
import com.lucas.sistemabancario.repository.AccountRepository;
import com.lucas.sistemabancario.repository.CheckingAccountRepository;
import com.lucas.sistemabancario.repository.SavingsAccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final CheckingAccountRepository checkingAccountRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final ClientService clientService;

    public AccountService(AccountRepository accountRepository, CheckingAccountRepository checkingAccountRepository, SavingsAccountRepository savingsAccountRepository, ClientService clientService) {
        this.accountRepository = accountRepository;
        this.checkingAccountRepository = checkingAccountRepository;
        this.savingsAccountRepository = savingsAccountRepository;
        this.clientService = clientService;
    }

    public List<AccountResponseDTO> findAll() {
        return accountRepository.findAll().stream().map(AccountResponseDTO::fromEntity).toList();
    }

    public Account findEntityById(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException("Conta não encontrada"));
    }

    public AccountResponseDTO findById(Long id) {
        Account account = findEntityById(id);
        return AccountResponseDTO.fromEntity(account);
    }

    @Transactional
    public AccountResponseDTO create(AccountRequestDTO dto) {
        Client client = clientService.findEntityById(dto.clientId());
        Account account;
        if (dto.accountType() == AccountType.CHECKING) {
            if (checkingAccountRepository.existsByClientId(dto.clientId())) {
                throw new AccountAlreadyExistsException("O cliente já possui uma conta corrente");
            }
            String accountNumber = generateAccountNumber();
            account = new CheckingAccount(client, accountNumber);
        } else if (dto.accountType() == AccountType.SAVINGS) {
            if (savingsAccountRepository.existsByClientId(dto.clientId())) {
                throw new AccountAlreadyExistsException("O cliente já possui uma conta poupança");
            }
            String accountNumber = generateAccountNumber();
            LocalDate lastYieldDate = LocalDate.now();
            account = new SavingsAccount(client, accountNumber, lastYieldDate);
        } else {
            throw new InvalidAccountTypeException("Tipo de conta inválido.");
        }
        Account savedAccount = accountRepository.save(account);

        return AccountResponseDTO.fromEntity(savedAccount);
    }

    @Transactional
    public void cancel(Long accountId) {
        Account account = findEntityById(accountId);
        validateActiveAccount(account);
        validateAccountHasNoBalance(account);
        account.cancel();
    }

    private String generateAccountNumber() {
        Long nextNumber = accountRepository.getNextAccountNumber();
        String baseNumber = String.format("%05d", nextNumber);
        int checkDigit = calculateCheckDigit(baseNumber);
        return baseNumber + checkDigit;
    }

    private int calculateCheckDigit(String baseNumber) {
        int sum = 0;
        int[] weights = {5, 4, 3, 2, 1};
        for (int i = 0; i < baseNumber.length(); i++) {
            int digit = Character.getNumericValue(baseNumber.charAt(i));
            sum += digit * weights[i];
        }
        return sum % 10;
    }

    private void validateAccountHasNoBalance(Account account) {
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new AccountHasBalanceException("Não é possível cancelar uma conta com saldo.");
        }
    }

    private void validateActiveAccount(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountIsNotActiveException("Não é possível cancelar uma conta que não está ativa.");
        }
    }
}
