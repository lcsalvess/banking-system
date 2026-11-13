package com.lcsalvess.bankingsystem.service.transaction.yield;

import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.service.audit.recording.AuditTrailRecorder;
import com.lcsalvess.bankingsystem.util.time.AppTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class DailyYieldScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyYieldScheduler.class);

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 200;

    private final AccountRepository accountRepository;
    private final DailyYieldService dailyYieldService;
    private final AuditTrailRecorder auditTrailRecorder;
    private final Clock clock;

    public DailyYieldScheduler(
            AccountRepository accountRepository,
            DailyYieldService dailyYieldService,
            AuditTrailRecorder auditTrailRecorder,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.dailyYieldService = dailyYieldService;
        this.auditTrailRecorder = auditTrailRecorder;
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

        String previousCorrelationId =
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

        MDC.put(
                CorrelationIdFilter.CORRELATION_ID_MDC_KEY,
                UUID.randomUUID().toString()
        );

        try {
            log.info(
                    "Starting daily yield processing for {}",
                    referenceDate
            );

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
            try {
                logMetrics(referenceDate, aborted, metrics);
                recordRun(referenceDate, runStatus(aborted, metrics), metrics);
            } finally {
                if (previousCorrelationId == null) {
                    MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
                } else {
                    MDC.put(
                            CorrelationIdFilter.CORRELATION_ID_MDC_KEY,
                            previousCorrelationId
                    );
                }
            }
        }
    }

    private static DailyYieldRunStatus runStatus(
            boolean aborted,
            YieldMetrics metrics
    ) {
        if (aborted) {
            return DailyYieldRunStatus.ABORTED;
        }

        return metrics.getFailed() > 0
                ? DailyYieldRunStatus.COMPLETED_WITH_FAILURES
                : DailyYieldRunStatus.COMPLETED;
    }

    private void recordRun(
            LocalDate referenceDate,
            DailyYieldRunStatus status,
            YieldMetrics metrics
    ) {
        // A startup recovery with nothing pending must not add a row per
        // restart to an append-only table. An aborted run is always recorded.
        if (metrics.getAttempted() == 0
                && status != DailyYieldRunStatus.ABORTED) {
            return;
        }

        auditTrailRecorder.recordYieldRun(
                referenceDate,
                status.outcome(),
                status.name()
        );
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
                return dailyYieldService.processDailyYieldForDate(
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

        // Final attempt: if it fails, the exception is propagated.
        return dailyYieldService.processDailyYieldForDate(
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
     * Encapsulates the daily yield processing metrics.
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

        private void incrementAttempted() {
            attempted++;
        }

        private void incrementSkipped() {
            skippedAccounts++;
        }

        private void incrementFailed() {
            failed++;
        }

        private int getAttempted() {
            return attempted;
        }

        private int getFailed() {
            return failed;
        }

        private void registerResult(DailyYieldProcessingResult result) {
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
