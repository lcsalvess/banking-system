package com.lcsalvess.bankingsystem.service.transaction.yield;

import com.lcsalvess.bankingsystem.config.yield.YieldExecutorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Recupera os rendimentos pendentes após a inicialização.
 *
 * <p>O processamento roda em outra thread: o Spring só marca a readiness como
 * {@code ACCEPTING_TRAFFIC} depois que todos os listeners de
 * {@link ApplicationReadyEvent} terminam, então um catch-up longo deixaria a
 * instância fora do ar para o orquestrador. O processamento é idempotente
 * (lock da conta + índice único {@code uk_transactions_daily_yield}),
 * portanto pode rodar em paralelo com o cron.
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
            @Qualifier(YieldExecutorConfig.YIELD_EXECUTOR)
            TaskExecutor taskExecutor
    ) {
        this.dailyYieldScheduler = dailyYieldScheduler;
        this.taskExecutor = taskExecutor;
    }

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