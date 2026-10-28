package com.lcsalvess.bankingsystem.service.transaction.yield;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.event.transaction.TransactionOperationEvent;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotSavingsException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.transaction.BalanceLimitExceededException;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.util.actor.SystemActor;
import com.lcsalvess.bankingsystem.util.time.AppTimeZone;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Processes an individual savings-account yield date.
 *
 * <p>The scheduler owns batching and retries; this service owns the
 * transactional business operation for one account and reference date.
 */
@Service
public class DailyYieldService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public DailyYieldService(
            TransactionRepository transactionRepository,
            AccountService accountService,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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

        Instant dayStart = referenceDate
                .atStartOfDay(AppTimeZone.ZONE)
                .toInstant();

        // Caminho rápido: lê o balance_after da última transação anterior ao
        // dia. Se o histórico não estiver em ordem (rendimento retroativo
        // gravado depois de movimentações mais novas), a consulta não retorna
        // nada e o saldo é reconstruído somando o histórico.
        BigDecimal balanceAtStartOfDay =
                transactionRepository.findBalanceAtStartOfDayFromSnapshot(
                                savingsAccount.getId(),
                                referenceDate,
                                dayStart
                        )
                        .orElseGet(() ->
                                transactionRepository.calculateBalanceAtStartOfDay(
                                        savingsAccount.getId(),
                                        referenceDate,
                                        dayStart
                                )
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
                        SystemActor.NAME
                )
        );

        return DailyYieldProcessingResult.APPLIED;
    }

    private void validateActiveAccount(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountIsNotActiveException(ApiErrorMessages.ACCOUNT_NOT_ACTIVE);
        }
    }

    private void validateBalanceLimit(Account account, BigDecimal amount) {
        if (!account.canCredit(amount)) {
            throw new BalanceLimitExceededException(ApiErrorMessages.BALANCE_LIMIT_EXCEEDED);
        }
    }

    private SavingsAccount validateAndGetSavingsAccount(Account account) {
        if (!(account instanceof SavingsAccount savingsAccount)) {
            throw new AccountIsNotSavingsException(ApiErrorMessages.ACCOUNT_NOT_SAVINGS);
        }
        return savingsAccount;
    }

    private void registerSystemYieldTransaction(
            BigDecimal amount,
            LocalDate referenceDate,
            SavingsAccount account
    ) {
        Transaction transaction = Transaction.createSystemYield(
                amount,
                clock.instant(),
                referenceDate,
                account,
                account.getBalance()
        );
        transactionRepository.save(transaction);
    }
}
