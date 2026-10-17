package com.lcsalvess.bankingsystem.service.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DailyYieldStartupRecovery {

    private static final Logger log =
            LoggerFactory.getLogger(DailyYieldStartupRecovery.class);

    private final DailyYieldScheduler dailyYieldScheduler;

    public DailyYieldStartupRecovery(
            DailyYieldScheduler dailyYieldScheduler
    ) {
        this.dailyYieldScheduler = dailyYieldScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingYieldsOnStartup() {
        log.info("Triggering daily yield recovery after application startup.");

        dailyYieldScheduler.processDailyYields();
    }
}