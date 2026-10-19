package com.lcsalvess.bankingsystem.service.transaction.yield;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Recupera os rendimentos pendentes após a inicialização.
 *
 * <p>O processamento roda em outra thread: o Spring só marca a readiness como
 * ACCEPTING_TRAFFIC depois que todos os listeners de {@link ApplicationReadyEvent}
 * terminam, então um catch-up longo deixaria a instância fora do ar para o
 * orquestrador. O processamento é idempotente (lock da conta + índice único
 * {@code uk_transactions_daily_yield}), portanto pode rodar em paralelo com o cron.
 */
@Component
@Profile("!test")
public class DailyYieldStartupRecovery {

    public static final String THREAD_NAME = "daily-yield-startup-recovery";

    private static final Logger log =
            LoggerFactory.getLogger(DailyYieldStartupRecovery.class);

    private final DailyYieldScheduler dailyYieldScheduler;
    private final Executor executor;

    @Autowired
    public DailyYieldStartupRecovery(DailyYieldScheduler dailyYieldScheduler) {
        this(
                dailyYieldScheduler,
                command -> Thread.ofPlatform().name(THREAD_NAME).start(command)
        );
    }

    public DailyYieldStartupRecovery(
            DailyYieldScheduler dailyYieldScheduler,
            Executor executor
    ) {
        this.dailyYieldScheduler = dailyYieldScheduler;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingYieldsOnStartup() {
        executor.execute(() -> {
            try {
                log.info("Triggering daily yield recovery after application startup.");

                dailyYieldScheduler.processDailyYields();

            } catch (RuntimeException ex) {
                log.error("Daily yield recovery after startup failed unexpectedly.", ex);
            }
        });
    }
}