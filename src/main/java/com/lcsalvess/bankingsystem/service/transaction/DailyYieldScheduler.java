package com.lcsalvess.bankingsystem.service.transaction;

import com.lcsalvess.bankingsystem.entity.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.enums.AccountStatus;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.transaction.BalanceLimitExceededException;
import com.lcsalvess.bankingsystem.exception.transaction.YieldAlreadyAppliedException;
import com.lcsalvess.bankingsystem.exception.transaction.YieldNotAvailableException;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class DailyYieldScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(DailyYieldScheduler.class);

    private static final int BATCH_SIZE = 100;

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
            cron = "${banking.yield.cron:0 5 0 * * *}",
            zone = "America/Sao_Paulo"
    )
    public void processDailyYields() {
        LocalDate referenceDate = LocalDate.now(clock);
        long lastId = 0L;

        int processed = 0;
        int applied = 0;
        int skipped = 0;
        int failed = 0;

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
                    processed++;

                    try {
                        transactionService.applyYieldForDate(
                                account.getAccountNumber(),
                                account.getDigit(),
                                referenceDate
                        );

                        applied++;

                    } catch (
                            YieldAlreadyAppliedException
                            | YieldNotAvailableException
                            | AccountIsNotActiveException ex
                    ) {
                        skipped++;

                        log.debug(
                                "Daily yield skipped for savings account {} on {}: {}",
                                account.getAccountNumber(),
                                referenceDate,
                                ex.getMessage()
                        );

                    } catch (BalanceLimitExceededException ex) {
                        skipped++;

                        log.warn(
                                "Daily yield could not be credited because the balance limit "
                                        + "would be exceeded for savings account {} on {}",
                                account.getAccountNumber(),
                                referenceDate
                        );

                    } catch (RuntimeException ex) {
                        failed++;

                        log.error(
                                "Failed to process daily yield for savings account {} on {}",
                                account.getAccountNumber(),
                                referenceDate,
                                ex
                        );
                    }
                }
            }

        } catch (RuntimeException ex) {
            log.error(
                    "Daily yield processing stopped while retrieving accounts after ID {}",
                    lastId,
                    ex
            );

        } finally {
            log.info(
                    "Daily yield processing completed for {}: "
                            + "processed={}, applied={}, skipped={}, failed={}",
                    referenceDate,
                    processed,
                    applied,
                    skipped,
                    failed
            );
        }
    }
}
