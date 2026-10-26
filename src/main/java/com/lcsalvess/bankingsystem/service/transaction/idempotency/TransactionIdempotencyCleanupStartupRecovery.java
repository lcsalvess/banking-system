package com.lcsalvess.bankingsystem.service.transaction.idempotency;

import com.lcsalvess.bankingsystem.config.executor.TaskExecutorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Removes expired idempotency records after application startup.
 *
 * <p>The cleanup runs asynchronously so that application readiness
 * does not depend on the completion of the cleanup task.
 */
@Component
@Profile("!test")
public class TransactionIdempotencyCleanupStartupRecovery {

    private static final Logger log =
            LoggerFactory.getLogger(
                    TransactionIdempotencyCleanupStartupRecovery.class
            );

    private final TransactionIdempotencyCleanupJob cleanupJob;
    private final TaskExecutor taskExecutor;

    public TransactionIdempotencyCleanupStartupRecovery(
            TransactionIdempotencyCleanupJob cleanupJob,
            @Qualifier(TaskExecutorConfig.STARTUP_RECOVERY_EXECUTOR)
            TaskExecutor taskExecutor
    ) {
        this.cleanupJob = cleanupJob;
        this.taskExecutor = taskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanUpExpiredRequestsOnStartup() {
        taskExecutor.execute(() -> {
            try {
                log.info(
                        "Triggering transaction idempotency cleanup after application startup."
                );

                cleanupJob.cleanUp();

            } catch (RuntimeException ex) {
                log.error(
                        "Transaction idempotency cleanup after startup failed unexpectedly.",
                        ex
                );
            }
        });
    }
}