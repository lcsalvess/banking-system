package com.lcsalvess.bankingsystem.unit.service.transaction;

import com.lcsalvess.bankingsystem.service.transaction.DailyYieldScheduler;
import com.lcsalvess.bankingsystem.service.transaction.DailyYieldStartupRecovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DailyYieldStartupRecoveryTests {

    @Mock
    private DailyYieldScheduler dailyYieldScheduler;

    private DailyYieldStartupRecovery startupRecovery;

    @BeforeEach
    void setUp() {
        startupRecovery = new DailyYieldStartupRecovery(
                dailyYieldScheduler
        );
    }

    @Test
    @DisplayName("Should trigger daily yield recovery after application startup")
    void shouldTriggerDailyYieldRecoveryAfterStartup() {
        startupRecovery.recoverPendingYieldsOnStartup();

        verify(dailyYieldScheduler).processDailyYields();
        verifyNoMoreInteractions(dailyYieldScheduler);
    }
}