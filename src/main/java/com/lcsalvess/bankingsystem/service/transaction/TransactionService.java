package com.lcsalvess.bankingsystem.service.transaction;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.event.transaction.TransactionOperationEvent;
import com.lcsalvess.bankingsystem.event.transaction.TransactionTransferEvent;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.account.AccountsAreSameException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.transaction.BalanceLimitExceededException;
import com.lcsalvess.bankingsystem.exception.transaction.InsufficientBalanceException;
import com.lcsalvess.bankingsystem.exception.transaction.InvalidAmountException;
import com.lcsalvess.bankingsystem.exception.transaction.TransactionNotFoundException;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.specification.TransactionSpecifications;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.account.LockedAccounts;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.util.pagination.PageableTieBreaker;
import com.lcsalvess.bankingsystem.util.time.AppTimeZone;
import com.lcsalvess.bankingsystem.validation.daterange.QueryDateRangeValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
        QueryDateRangeValidator.validate(startDate, endDate, clock);

        Account account = accountService.findEntityByAccountNumber(
                accountNumber,
                accountDigit
        );

        return transactionRepository.findAll(
                TransactionSpecifications.byAccountAndFilters(
                        account.getId(),
                        type,
                        startDate != null
                                ? startDate.atStartOfDay(AppTimeZone.ZONE).toInstant()
                                : null,
                        endDate != null
                                ? endDate.plusDays(1)
                                .atStartOfDay(AppTimeZone.ZONE)
                                .toInstant()
                                : null
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
        eventPublisher.publishEvent(new TransactionOperationEvent(TransactionType.DEPOSIT, account.getAccountNumber(), dto.amount(), username, transaction.getTransactionCode()));

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
        eventPublisher.publishEvent(new TransactionOperationEvent(TransactionType.WITHDRAWAL, account.getAccountNumber(), dto.amount(), username, transaction.getTransactionCode()));

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
                        username,
                        transferCode
                )
        );

        return TransactionResponseDTO.fromEntity(sentTransaction);
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

    private void validateDistinctAccounts(String fromAccountNumber, String toAccountNumber) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new AccountsAreSameException(ApiErrorMessages.SAME_SOURCE_AND_DESTINATION_ACCOUNT);
        }
    }

    private Transaction registerTransaction(TransactionType type, BigDecimal amount, Account account, User performedBy) {
        Transaction transaction = Transaction.create(
                type,
                amount,
                clock.instant(),
                account,
                performedBy,
                account.getBalance()
        );
        return transactionRepository.save(transaction);
    }

    private Transaction registerTransferTransaction(
            TransactionType type,
            BigDecimal amount,
            Account account,
            UUID transferCode,
            User performedBy
    ) {
        Transaction transaction = Transaction.createTransfer(
                type,
                amount,
                clock.instant(),
                account,
                transferCode,
                performedBy,
                account.getBalance()
        );

        return transactionRepository.save(transaction);
    }
}
