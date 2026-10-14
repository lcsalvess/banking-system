package com.lcsalvess.bankingsystem.service.transaction;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.Account;
import com.lcsalvess.bankingsystem.entity.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.Transaction;
import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.AccountStatus;
import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import com.lcsalvess.bankingsystem.event.transaction.TransactionOperationEvent;
import com.lcsalvess.bankingsystem.event.transaction.TransactionTransferEvent;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotSavingsException;
import com.lcsalvess.bankingsystem.exception.account.AccountsAreSameException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.transaction.*;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.specification.TransactionSpecifications;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.account.LockedAccounts;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TransactionService(TransactionRepository transactionRepository, AccountService accountService, CurrentUserService currentUserService, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.currentUserService = currentUserService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponseDTO> findByAccountNumber(
            String accountNumber,
            String accountDigit,
            TransactionType type,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    ) {
        validateDateRange(startDate, endDate);

        Account account = accountService.findEntityByAccountNumber(
                accountNumber,
                accountDigit
        );

        return transactionRepository.findAll(
                TransactionSpecifications.byAccountAndFilters(
                        account.getId(),
                        type,
                        startDate != null ? startDate.atStartOfDay() : null,
                        endDate != null ? endDate.plusDays(1).atStartOfDay() : null
                ),
                pageable
        ).map(TransactionResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public TransactionResponseDTO findByTransactionCode(UUID transactionCode) {
        Transaction transaction = transactionRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new TransactionNotFoundException(
                        ApiErrorMessages.TRANSACTION_NOT_FOUND
                ));

        return TransactionResponseDTO.fromEntity(transaction);
    }

    @Transactional
    public TransactionResponseDTO deposit(AccountOperationRequestDTO dto) {
        validateAmount(dto.amount());

        Account account = accountService.findEntityByAccountNumberForUpdate(dto.accountNumber(), dto.digit());

        validateActiveAccount(account);

        User performedBy = currentUserService.getCurrentUser();
        String username = performedBy.getUsername();

        account.credit(dto.amount());

        Transaction transaction = registerTransaction(
                TransactionType.DEPOSIT,
                dto.amount(),
                account,
                performedBy
        );
        eventPublisher.publishEvent(new TransactionOperationEvent(TransactionType.DEPOSIT, account.getAccountNumber(), dto.amount(), username));

        return TransactionResponseDTO.fromEntity(transaction);
    }

    @Transactional
    public TransactionResponseDTO withdraw(AccountOperationRequestDTO dto) {
        validateAmount(dto.amount());

        Account account = accountService.findEntityByAccountNumberForUpdate(dto.accountNumber(), dto.digit());

        validateActiveAccount(account);
        validateBalance(account, dto.amount());

        User performedBy = currentUserService.getCurrentUser();
        String username = performedBy.getUsername();

        account.debit(dto.amount());

        Transaction transaction = registerTransaction(
                TransactionType.WITHDRAWAL,
                dto.amount(),
                account,
                performedBy
        );
        eventPublisher.publishEvent(new TransactionOperationEvent(TransactionType.WITHDRAWAL, account.getAccountNumber(), dto.amount(), username));

        return TransactionResponseDTO.fromEntity(transaction);
    }

    @Transactional
    public TransactionResponseDTO transfer(TransferRequestDTO dto) {
        validateDistinctAccounts(
                dto.fromAccountNumber(),
                dto.toAccountNumber()
        );
        validateAmount(dto.amount());

        LockedAccounts lockedAccounts =
                accountService.lockAccountsForTransfer(
                        dto.fromAccountNumber(),
                        dto.fromAccountDigit(),
                        dto.toAccountNumber(),
                        dto.toAccountDigit()
                );

        Account fromAccount = lockedAccounts.fromAccount();
        Account toAccount = lockedAccounts.toAccount();

        validateActiveAccount(fromAccount);
        validateActiveAccount(toAccount);
        validateBalance(fromAccount, dto.amount());

        User performedBy = currentUserService.getCurrentUser();
        String username = performedBy.getUsername();

        fromAccount.debit(dto.amount());
        toAccount.credit(dto.amount());

        UUID transferCode = UUID.randomUUID();

        Transaction sentTransaction =
                registerTransferTransaction(
                        TransactionType.TRANSFER_SENT,
                        dto.amount(),
                        fromAccount,
                        transferCode,
                        performedBy
                );

        registerTransferTransaction(
                TransactionType.TRANSFER_RECEIVED,
                dto.amount(),
                toAccount,
                transferCode,
                performedBy
        );

        eventPublisher.publishEvent(
                new TransactionTransferEvent(
                        TransactionType.TRANSFER_SENT,
                        fromAccount.getAccountNumber(),
                        toAccount.getAccountNumber(),
                        dto.amount(),
                        username
                )
        );

        return TransactionResponseDTO.fromEntity(sentTransaction);
    }

    @Transactional
    public TransactionResponseDTO applyYield(String accountNumber, String accountDigit) {
        Account account = accountService.findEntityByAccountNumberForUpdate(accountNumber, accountDigit);

        SavingsAccount savingsAccount = validateAndGetSavingsAccount(account);

        validateActiveAccount(savingsAccount);
        validateYieldAlreadyApplied(savingsAccount.getId());

        BigDecimal yieldAmount = savingsAccount.calculateYield();
        validateYieldAvailable(savingsAccount, yieldAmount);

        User performedBy = currentUserService.getCurrentUser();
        String username = performedBy.getUsername();

        savingsAccount.credit(yieldAmount);
        savingsAccount.updateLastYieldDate();

        Transaction transaction = registerTransaction(TransactionType.YIELD, yieldAmount, savingsAccount, performedBy);
        eventPublisher.publishEvent(new TransactionOperationEvent(TransactionType.YIELD, savingsAccount.getAccountNumber(), yieldAmount, username));

        return TransactionResponseDTO.fromEntity(transaction);
    }

    private void validateActiveAccount(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountIsNotActiveException(ApiErrorMessages.ACCOUNT_NOT_ACTIVE);
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(ApiErrorMessages.INVALID_AMOUNT);
        }
    }

    private void validateBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(ApiErrorMessages.INSUFFICIENT_BALANCE);
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidDateRangeException(ApiErrorMessages.INVALID_DATE_RANGE);
        }
    }

    private void validateDistinctAccounts(String fromAccountNumber, String toAccountNumber) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new AccountsAreSameException(ApiErrorMessages.SAME_SOURCE_AND_DESTINATION_ACCOUNT);
        }
    }

    private SavingsAccount validateAndGetSavingsAccount(Account account) {
        if (!(account instanceof SavingsAccount savingsAccount)) {
            throw new AccountIsNotSavingsException(ApiErrorMessages.ACCOUNT_NOT_SAVINGS);
        }
        return savingsAccount;
    }

    private void validateYieldAvailable(SavingsAccount savingsAccount, BigDecimal yieldAmount) {
        if (!savingsAccount.isEligibleForYield(LocalDate.now(clock))) {
            throw new YieldNotAvailableException(ApiErrorMessages.YIELD_NOT_AVAILABLE);
        }
        if (yieldAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new YieldNotAvailableException(ApiErrorMessages.NO_YIELD_AVAILABLE);
        }
    }

    private void validateYieldAlreadyApplied(Long accountId) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endExclusive = today.plusDays(1).atStartOfDay();

        boolean alreadyApplied =
                transactionRepository
                        .existsByAccountIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                accountId,
                                TransactionType.YIELD,
                                startOfDay,
                                endExclusive
                        );

        if (alreadyApplied) {
            throw new YieldAlreadyAppliedException(
                    ApiErrorMessages.DAILY_YIELD_ALREADY_APPLIED
            );
        }
    }

    private Transaction registerTransaction(TransactionType type, BigDecimal amount, Account account, User performedBy) {
        Transaction transaction = new Transaction(type, amount, LocalDateTime.now(clock), account, performedBy);
        return transactionRepository.save(transaction);
    }

    private Transaction registerTransferTransaction(
            TransactionType type,
            BigDecimal amount,
            Account account,
            UUID transferCode,
            User performedBy
    ) {
        Transaction transaction =
                new Transaction(
                        type,
                        amount,
                        LocalDateTime.now(clock),
                        account,
                        transferCode,
                        performedBy
                );

        return transactionRepository.save(transaction);
    }
}