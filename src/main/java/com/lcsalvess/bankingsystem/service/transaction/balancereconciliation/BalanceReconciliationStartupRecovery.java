package com.lcsalvess.bankingsystem.service.transaction.balancereconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class BalanceReconciliationStartupRecovery {

    private static final Logger log =
            LoggerFactory.getLogger(BalanceReconciliationStartupRecovery.class);

    private static final int MAX_ATTEMPTS = 3;

    private static final long[] RETRY_DELAYS_MILLIS = {
            1_000L,
            5_000L
    };

    private final BalanceReconciliationJob balanceReconciliationJob;
    private final TaskExecutor startupRecoveryExecutor;

    public BalanceReconciliationStartupRecovery(
            BalanceReconciliationJob balanceReconciliationJob,
            @Qualifier("startupRecoveryExecutor")
            TaskExecutor startupRecoveryExecutor
    ) {
        this.balanceReconciliationJob = balanceReconciliationJob;
        this.startupRecoveryExecutor = startupRecoveryExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            startupRecoveryExecutor.execute(this::reconcileWithRetries);
        } catch (RuntimeException ex) {
            log.error(
                    "Could not schedule startup balance reconciliation.",
                    ex
            );
        }
    }

    void reconcileWithRetries() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                int affectedAccounts = balanceReconciliationJob.reconcile();

                log.info(
                        "Startup balance reconciliation completed: "
                                + "affectedAccounts={}",
                        affectedAccounts
                );

                return;
            } catch (RuntimeException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error(
                            "Startup balance reconciliation failed after "
                                    + "{} attempts. The next scheduled run "
                                    + "may retry.",
                            MAX_ATTEMPTS,
                            ex
                    );
                    // A execução termina naturalmente após a última tentativa.
                } else {
                    long delayMillis = RETRY_DELAYS_MILLIS[attempt - 1];

                    log.warn(
                            "Startup balance reconciliation attempt {} of {} "
                                    + "failed. Retrying in {} ms. Cause: {}",
                            attempt,
                            MAX_ATTEMPTS,
                            delayMillis,
                            ex.getMessage()
                    );

                    if (!waitBeforeRetry(delayMillis)) {
                        return;
                    }
                }
            }
        }
    }

    protected boolean waitBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Startup balance reconciliation retries were interrupted."
            );
            return false;
        }
    }
}