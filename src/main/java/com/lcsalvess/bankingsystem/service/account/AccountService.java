package com.lcsalvess.bankingsystem.service.account;

import com.lcsalvess.bankingsystem.dto.request.AccountRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.AccountResponseDTO;
import com.lcsalvess.bankingsystem.entity.Account;
import com.lcsalvess.bankingsystem.entity.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.Client;
import com.lcsalvess.bankingsystem.entity.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.enums.AccountStatus;
import com.lcsalvess.bankingsystem.entity.enums.AccountType;
import com.lcsalvess.bankingsystem.event.account.AccountOperationEvent;
import com.lcsalvess.bankingsystem.event.account.AccountOperationType;
import com.lcsalvess.bankingsystem.exception.account.*;
import com.lcsalvess.bankingsystem.exception.database.DatabaseConstraint;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    private final AccountNumberGenerator accountNumberGenerator;

    private final ClientService clientService;

    private final CurrentUserService currentUserService;

    private final ApplicationEventPublisher eventPublisher;

    public AccountService(AccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator, ClientService clientService, CurrentUserService currentUserService, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
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

    public Account findEntityByAccountNumberForUpdate(String accountNumber, String accountDigit) {
        validateDigit(accountNumber, accountDigit);

        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada."));
    }

    @Transactional
    public AccountResponseDTO create(AccountRequestDTO dto) {
        Client client = clientService.findEntityById(dto.clientId());

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
            if (DatabaseConstraint.ACCOUNT_CLIENT_TYPE_ACTIVE_UNIQUE.isViolatedBy(exception)) {
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