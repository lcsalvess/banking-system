package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.AccountRequestDTO;
import com.lucas.bankingsystem.dto.response.AccountResponseDTO;
import com.lucas.bankingsystem.entity.Account;
import com.lucas.bankingsystem.entity.CheckingAccount;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.entity.SavingsAccount;
import com.lucas.bankingsystem.entity.enums.AccountStatus;
import com.lucas.bankingsystem.entity.enums.AccountType;
import com.lucas.bankingsystem.event.account.AccountOperationEvent;
import com.lucas.bankingsystem.event.account.AccountOperationType;
import com.lucas.bankingsystem.exception.account.*;
import com.lucas.bankingsystem.repository.AccountRepository;
import com.lucas.bankingsystem.repository.CheckingAccountRepository;
import com.lucas.bankingsystem.repository.SavingsAccountRepository;
import com.lucas.bankingsystem.service.account.AccountNumberGenerator;
import com.lucas.bankingsystem.service.account.GeneratedAccountNumber;
import com.lucas.bankingsystem.service.security.CurrentUserService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    private final CheckingAccountRepository checkingAccountRepository;

    private final SavingsAccountRepository savingsAccountRepository;

    private final AccountNumberGenerator accountNumberGenerator;

    private final ClientService clientService;

    private final CurrentUserService currentUserService;

    private final ApplicationEventPublisher eventPublisher;

    public AccountService(AccountRepository accountRepository, CheckingAccountRepository checkingAccountRepository, SavingsAccountRepository savingsAccountRepository, AccountNumberGenerator accountNumberGenerator, ClientService clientService, CurrentUserService currentUserService, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.checkingAccountRepository = checkingAccountRepository;
        this.savingsAccountRepository = savingsAccountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.clientService = clientService;
        this.currentUserService = currentUserService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<AccountResponseDTO> findAll() {
        return accountRepository.findAllWithClient().stream().map(AccountResponseDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public Account findEntityByAccountNumber(String accountNumber, String accountDigit) {
        validateDigit(accountNumber, accountDigit);
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new AccountNotFoundException("Conta não encontrada."));
    }

    @Transactional(readOnly = true)
    public AccountResponseDTO findByAccountNumber(String accountNumber, String accountDigit) {
       validateDigit(accountNumber, accountDigit);
       Account account = accountRepository.findByAccountNumberWithClient(accountNumber)
               .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada."));
        return AccountResponseDTO.fromEntity(account);
    }

    @Transactional
    public AccountResponseDTO create(AccountRequestDTO dto) {
        Client client = clientService.findEntityById(dto.clientId());

        validateAccountTypeAndAvailability(dto);

        String username = getCurrentUsername();

        GeneratedAccountNumber accountNumber = accountNumberGenerator.generate();

        Account account = createAccount(dto, client, accountNumber);

        Account savedAccount = saveAccount(account);

        eventPublisher.publishEvent(
                new AccountOperationEvent(savedAccount.getId(), AccountOperationType.CREATED, username)
        );

        return AccountResponseDTO.fromEntity(savedAccount);
    }

    @Transactional
    public void cancel(String accountNumber, String accountDigit) {
        Account account = findEntityByAccountNumber(accountNumber, accountDigit);

        validateActiveAccount(account);
        validateAccountHasNoBalance(account);

        String username = getCurrentUsername();

        account.cancel();

        eventPublisher.publishEvent(
                new AccountOperationEvent(account.getId(), AccountOperationType.CANCELLED, username)
        );
    }

    private void validateDigit(String accountNumber, String accountDigit) {
        if (!accountNumberGenerator.isValid(accountNumber, accountDigit)) {
            throw new InvalidAccountDigitException("Dígito da conta inválido.");
        }
    }

    private void validateAccountTypeAndAvailability(AccountRequestDTO dto) {
        if (dto.type() == AccountType.CHECKING &&
                checkingAccountRepository.existsByClientIdAndStatus(dto.clientId(), AccountStatus.ACTIVE)) {
            throw new AccountAlreadyExistsException("O cliente já possui uma conta corrente.");
        }

        if (dto.type() == AccountType.SAVINGS &&
                savingsAccountRepository.existsByClientIdAndStatus(dto.clientId(), AccountStatus.ACTIVE)) {
            throw new AccountAlreadyExistsException("O cliente já possui uma conta poupança.");
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

    private boolean isDuplicateActiveAccount(DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return "uk_accounts_client_type_active".equals(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String getDuplicateAccountMessage(AccountType type) {
        return switch (type) {
            case CHECKING -> "O cliente já possui uma conta corrente.";
            case SAVINGS -> "O cliente já possui uma conta poupança.";
        };
    }

    private Account saveAccount(Account account) {
        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateActiveAccount(exception)) {
                throw new AccountAlreadyExistsException(
                        getDuplicateAccountMessage(account.getType())
                );
            }
            throw exception;
        }
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

    private String getCurrentUsername() {
        return currentUserService.getUsername();
    }
}
