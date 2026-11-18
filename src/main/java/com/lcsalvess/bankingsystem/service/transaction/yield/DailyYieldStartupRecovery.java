package com.lcsalvess.bankingsystem.service.transaction.yield;

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
 * Recovers pending yields after application startup.
 *
 * <p>Processing runs on a separate thread: Spring only marks the application
 * readiness state as {@code ACCEPTING_TRAFFIC} after all
 * {@link ApplicationReadyEvent} listeners have finished. Therefore, a lengthy
 * catch-up process would leave the instance unavailable to the orchestrator.
 *
 * <p>Processing is idempotent (account locking + the unique
 * {@code uk_transactions_daily_yield} index), so it can run concurrently
 * with the scheduled cron job.
 */
@Component
@Profile("!test")
public class DailyYieldStartupRecovery {

  private static final Logger log =
      LoggerFactory.getLogger(DailyYieldStartupRecovery.class);

  private final DailyYieldScheduler dailyYieldScheduler;
  private final TaskExecutor taskExecutor;

  public DailyYieldStartupRecovery(
      DailyYieldScheduler dailyYieldScheduler,
      @Qualifier(TaskExecutorConfig.STARTUP_RECOVERY_EXECUTOR)
      TaskExecutor taskExecutor
  ) {
    this.dailyYieldScheduler = dailyYieldScheduler;
    this.taskExecutor = taskExecutor;
  }

  /**
   * Schedules the recovery of pending daily yields after application startup.
   *
   * <p>Executes the recovery process asynchronously using a dedicated task executor
   * to prevent application readiness from being blocked by yield processing.
   *
   * <p>Unexpected runtime exceptions during recovery are logged without interrupting
   * the application.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void recoverPendingYieldsOnStartup() {
    taskExecutor.execute(() -> {
      try {
        log.info(
            "Triggering daily yield recovery after application startup."
        );

        dailyYieldScheduler.processDailyYields();

      } catch (RuntimeException ex) {
        log.error(
            "Daily yield recovery after startup failed unexpectedly.",
            ex
        );
      }
    });
  }
}
