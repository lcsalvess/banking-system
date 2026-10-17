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
import com.lcsalvess.bankingsystem.util.PageableTieBreaker;
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

    private static final LocalDate MIN_QUERY_DATE =
            LocalDate.of(2020, 1, 1);

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
                PageableTieBreaker.withIdTieBreaker(pageable)
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
        validateBalanceLimit(account, dto.amount());

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
        validateBalanceLimit(toAccount, dto.amount());

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
    public DailyYieldProcessingResult processDailyYieldForDate(
            String accountNumber,
            String accountDigit,
            LocalDate referenceDate
    ) {
        if (referenceDate == null) {
            throw new IllegalArgumentException(
                    "A data de referência do rendimento é obrigatória."
            );
        }

        if (referenceDate.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException(
                    "A data de referência não pode estar no futuro."
            );
        }

        Account account = accountService.findEntityByAccountNumberForUpdate(
                accountNumber,
                accountDigit
        );

        SavingsAccount savingsAccount =
                validateAndGetSavingsAccount(account);

        validateActiveAccount(savingsAccount);

        LocalDate lastProcessedDate =
                savingsAccount.getLastYieldProcessedDate();

        if (!referenceDate.isAfter(lastProcessedDate)) {
            return DailyYieldProcessingResult.ALREADY_PROCESSED;
        }

        if (!referenceDate.equals(lastProcessedDate.plusDays(1))) {
            throw new IllegalStateException(
                    "As datas de rendimento devem ser processadas em ordem."
            );
        }

        boolean alreadyApplied =
                transactionRepository.existsByAccountIdAndTypeAndYieldDate(
                        savingsAccount.getId(),
                        TransactionType.YIELD,
                        referenceDate
                );

        if (alreadyApplied) {
            if (referenceDate.isAfter(savingsAccount.getLastYieldDate())) {
                savingsAccount.updateLastYieldDate(referenceDate);
            }

            savingsAccount.markYieldDateProcessed(referenceDate);

            return DailyYieldProcessingResult.ALREADY_PROCESSED;
        }

        if (!savingsAccount.isEligibleForYield(referenceDate)) {
            savingsAccount.markYieldDateProcessed(referenceDate);

            return DailyYieldProcessingResult.NOT_ELIGIBLE;
        }

        BigDecimal balanceAtStartOfDay =
                transactionRepository.calculateBalanceAtStartOfDay(
                        savingsAccount.getId(),
                        referenceDate,
                        referenceDate.atStartOfDay()
                );

        BigDecimal yieldAmount =
                savingsAccount.calculateYield(balanceAtStartOfDay);

        if (yieldAmount.signum() <= 0) {
            savingsAccount.markYieldDateProcessed(referenceDate);

            return DailyYieldProcessingResult.NO_YIELD_AVAILABLE;
        }

        if (!savingsAccount.canCredit(yieldAmount)) {
            savingsAccount.markYieldDateProcessed(referenceDate);

            return DailyYieldProcessingResult.BALANCE_LIMIT_REACHED;
        }

        savingsAccount.credit(yieldAmount);
        savingsAccount.updateLastYieldDate(referenceDate);
        savingsAccount.markYieldDateProcessed(referenceDate);

        registerSystemYieldTransaction(
                yieldAmount,
                referenceDate,
                savingsAccount
        );

        eventPublisher.publishEvent(
                new TransactionOperationEvent(
                        TransactionType.YIELD,
                        savingsAccount.getAccountNumber(),
                        yieldAmount,
                        "SYSTEM"
                )
        );

        return DailyYieldProcessingResult.APPLIED;
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

    private void validateBalanceLimit(Account account, BigDecimal amount) {
        if (!account.canCredit(amount)) {
            throw new BalanceLimitExceededException(ApiErrorMessages.BALANCE_LIMIT_EXCEEDED);
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(clock);

        if (isInvalidQueryDate(startDate, today)
                || isInvalidQueryDate(endDate, today)) {
            throw new InvalidDateRangeException(ApiErrorMessages.INVALID_QUERY_DATE);
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException(ApiErrorMessages.INVALID_DATE_RANGE);
        }
    }

    private boolean isInvalidQueryDate(LocalDate date, LocalDate today) {
        return date != null
                && (date.isBefore(MIN_QUERY_DATE) || date.isAfter(today));
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

    private void registerSystemYieldTransaction(
            BigDecimal amount,
            LocalDate referenceDate,
            SavingsAccount account
    ) {
        Transaction transaction = new Transaction(
                TransactionType.YIELD,
                amount,
                LocalDateTime.now(clock),
                referenceDate,
                account
        );

        transactionRepository.save(transaction);
    }
}