package com.lcsalvess.bankingsystem.service.transaction.yield;

import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import com.lcsalvess.bankingsystem.util.time.AppTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class DailyYieldScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyYieldScheduler.class);

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 200;

    private final AccountRepository accountRepository;
    private final TransactionService transactionService;
    private final Clock clock;

    public DailyYieldScheduler(
            AccountRepository accountRepository,
            TransactionService transactionService,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${banking.yield.cron}",
            zone = AppTimeZone.ID
    )
    public void processDailyYields() {
        LocalDate referenceDate = LocalDate.now(clock);
        long lastId = 0L;

        YieldMetrics metrics = new YieldMetrics();
        boolean aborted = false;

        log.info(
                "Starting daily yield processing for {}",
                referenceDate
        );

        try {
            while (true) {
                List<SavingsAccount> batch =
                        accountRepository.findNextSavingsAccountsByStatus(
                                AccountStatus.ACTIVE,
                                lastId,
                                PageRequest.of(0, BATCH_SIZE)
                        );

                if (batch.isEmpty()) {
                    break;
                }

                for (SavingsAccount account : batch) {
                    lastId = account.getId();

                    processAccountYields(
                            account,
                            referenceDate,
                            metrics
                    );
                }
            }

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            aborted = true;

            log.warn(
                    "Daily yield processing was interrupted after account ID {}",
                    lastId,
                    ex
            );

        } catch (RuntimeException ex) {
            aborted = true;

            log.error(
                    "Daily yield processing stopped while retrieving accounts after ID {}",
                    lastId,
                    ex
            );

        } finally {
            logMetrics(referenceDate, aborted, metrics);
        }
    }

    private void processAccountYields(
            SavingsAccount account,
            LocalDate referenceDate,
            YieldMetrics metrics
    ) throws InterruptedException {

        LocalDate processingDate =
                account.getLastYieldProcessedDate().plusDays(1);

        while (!processingDate.isAfter(referenceDate)) {
            metrics.incrementAttempted();

            try {
                DailyYieldProcessingResult result =
                        processWithRetry(account, processingDate);

                metrics.registerResult(result);

            } catch (AccountIsNotActiveException ex) {
                metrics.incrementSkipped();

                log.info(
                        "Daily yield recovery stopped for inactive "
                                + "savings account {} on {}",
                        account.getAccountNumber(),
                        processingDate
                );

                break;

            } catch (RuntimeException ex) {
                metrics.incrementFailed();

                log.error(
                        "Daily yield recovery stopped for savings account "
                                + "{} on {}. The date remains pending.",
                        account.getAccountNumber(),
                        processingDate,
                        ex
                );

                break;
            }

            processingDate = processingDate.plusDays(1);
        }
    }

    private DailyYieldProcessingResult processWithRetry(
            SavingsAccount account,
            LocalDate referenceDate
    ) throws InterruptedException {

        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return transactionService.processDailyYieldForDate(
                        account.getAccountNumber(),
                        account.getDigit(),
                        referenceDate
                );

            } catch (TransientDataAccessException
                     | DataAccessResourceFailureException
                     | CannotCreateTransactionException ex) {

                log.warn(
                        "Failed to process yield for account {} "
                                + "on {}. Attempt {}/{}. "
                                + "A retry will be performed.",
                        account.getAccountNumber(),
                        referenceDate,
                        attempt,
                        MAX_ATTEMPTS,
                        ex
                );

                Thread.sleep(RETRY_DELAY_MILLIS * attempt);
            }
        }

        // Última tentativa: se falhar, a exceção será propagada.
        return transactionService.processDailyYieldForDate(
                account.getAccountNumber(),
                account.getDigit(),
                referenceDate
        );
    }

    private void logMetrics(
            LocalDate referenceDate,
            boolean aborted,
            YieldMetrics metrics
    ) {
        String status = aborted
                ? "aborted"
                : metrics.getFailed() > 0
                ? "completed with failures"
                : "completed";

        log.info(
                "Daily yield processing {} for {}: {}",
                status,
                referenceDate,
                metrics
        );
    }

    /**
     * Encapsula as métricas do processamento diário de rendimentos.
     */
    private static class YieldMetrics {

        private int attempted;
        private int applied;
        private int alreadyProcessed;
        private int notEligible;
        private int noYield;
        private int balanceLimitReached;
        private int skippedAccounts;
        private int failed;

        public void incrementAttempted() {
            attempted++;
        }

        public void incrementSkipped() {
            skippedAccounts++;
        }

        public void incrementFailed() {
            failed++;
        }

        public int getFailed() {
            return failed;
        }

        public void registerResult(DailyYieldProcessingResult result) {
            switch (result) {
                case APPLIED -> applied++;
                case ALREADY_PROCESSED -> alreadyProcessed++;
                case NOT_ELIGIBLE -> notEligible++;
                case NO_YIELD_AVAILABLE -> noYield++;
                case BALANCE_LIMIT_REACHED -> balanceLimitReached++;
            }
        }

        @Override
        public String toString() {
            return String.format(
                    "attempted=%d, applied=%d, alreadyProcessed=%d, "
                            + "notEligible=%d, noYield=%d, "
                            + "balanceLimitReached=%d, "
                            + "skippedAccounts=%d, failed=%d",
                    attempted,
                    applied,
                    alreadyProcessed,
                    notEligible,
                    noYield,
                    balanceLimitReached,
                    skippedAccounts,
                    failed
            );
        }
    }
}