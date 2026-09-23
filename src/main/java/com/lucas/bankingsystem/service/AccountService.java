package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.AccountRequestDTO;
import com.lucas.bankingsystem.dto.response.AccountResponseDTO;
import com.lucas.bankingsystem.entity.Account;
import com.lucas.bankingsystem.entity.CheckingAccount;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.entity.SavingsAccount;
import com.lucas.bankingsystem.entity.enums.AccountStatus;
import com.lucas.bankingsystem.entity.enums.AccountType;
import com.lucas.bankingsystem.exception.account.*;
import com.lucas.bankingsystem.repository.AccountRepository;
import com.lucas.bankingsystem.repository.CheckingAccountRepository;
import com.lucas.bankingsystem.repository.SavingsAccountRepository;
import com.lucas.bankingsystem.service.account.AccountNumberGenerator;
import com.lucas.bankingsystem.service.account.GeneratedAccountNumber;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final CheckingAccountRepository checkingAccountRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final ClientService clientService;

    public AccountService(AccountRepository accountRepository, CheckingAccountRepository checkingAccountRepository, SavingsAccountRepository savingsAccountRepository, AccountNumberGenerator accountNumberGenerator, ClientService clientService) {
        this.accountRepository = accountRepository;
        this.checkingAccountRepository = checkingAccountRepository;
        this.savingsAccountRepository = savingsAccountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.clientService = clientService;
    }

    public List<AccountResponseDTO> findAll() {
        return accountRepository.findAll().stream().map(AccountResponseDTO::fromEntity).toList();
    }

    public Account findEntityByAccountNumber(String accountNumber, String accountDigit) {
        validateDigit(accountNumber, accountDigit);
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new AccountNotFoundException("Conta não encontrada"));
    }

    public AccountResponseDTO findByAccountNumber(String accountNumber, String accountDigit) {
        Account account = findEntityByAccountNumber(accountNumber, accountDigit);
        return AccountResponseDTO.fromEntity(account);
    }

    @Transactional
    public AccountResponseDTO create(AccountRequestDTO dto) {
        Client client = clientService.findEntityById(dto.clientId());
        validateAccountTypeAndAvailability(dto);
        GeneratedAccountNumber accountNumber = accountNumberGenerator.generate();
        Account account = createAccount(dto, client, accountNumber);
        Account savedAccount = accountRepository.save(account);
        return AccountResponseDTO.fromEntity(savedAccount);
    }

    @Transactional
    public void cancel(String accountNumber, String accountDigit) {
        Account account = findEntityByAccountNumber(accountNumber, accountDigit);
        validateActiveAccount(account);
        validateAccountHasNoBalance(account);
        account.cancel();
    }

    private void validateDigit(String accountNumber, String accountDigit) {
        if (!accountNumberGenerator.isValid(accountNumber, accountDigit)) {
            throw new InvalidAccountDigitException("Digito da conta inválido.");
        }
    }

    private void validateAccountTypeAndAvailability(AccountRequestDTO dto) {
        if (dto.type() == AccountType.CHECKING &&
                checkingAccountRepository.existsByClientId(dto.clientId())) {
            throw new AccountAlreadyExistsException("O cliente já possui uma conta corrente.");
        }

        if (dto.type() == AccountType.SAVINGS &&
                savingsAccountRepository.existsByClientId(dto.clientId())) {
            throw new AccountAlreadyExistsException("O cliente já possui uma conta poupança.");
        }

        if (dto.type() != AccountType.CHECKING &&
                dto.type() != AccountType.SAVINGS) {
            throw new InvalidAccountTypeException("Tipo de conta inválido.");
        }
    }

    private Account createAccount(AccountRequestDTO dto, Client client, GeneratedAccountNumber accountNumber) {
        if (dto.type() == AccountType.CHECKING) {
            return new CheckingAccount(
                    client,
                    accountNumber.number(),
                    accountNumber.digit()
            );
        }
        return new SavingsAccount(
                client,
                accountNumber.number(),
                accountNumber.digit()
        );
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
