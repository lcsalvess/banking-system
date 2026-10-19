package com.lcsalvess.bankingsystem.unit.service.transaction.yield;

import com.lcsalvess.bankingsystem.service.transaction.yield.DailyYieldScheduler;
import com.lcsalvess.bankingsystem.service.transaction.yield.DailyYieldStartupRecovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DailyYieldStartupRecoveryTests {

    @Mock
    private DailyYieldScheduler dailyYieldScheduler;

    @Mock
    private TaskExecutor taskExecutor;

    @Captor
    private ArgumentCaptor<Runnable> taskCaptor;

    private DailyYieldStartupRecovery startupRecovery;

    @BeforeEach
    void setUp() {
        startupRecovery = new DailyYieldStartupRecovery(
                dailyYieldScheduler,
                taskExecutor
        );
    }

    @Test
    @DisplayName(
            "Should submit the recovery to the executor without running it "
                    + "on the calling thread"
    )
    void shouldSubmitRecoveryWithoutRunningItOnCallingThread() {
        startupRecovery.recoverPendingYieldsOnStartup();

        verify(taskExecutor).execute(taskCaptor.capture());
        verifyNoInteractions(dailyYieldScheduler);
    }

    @Test
    @DisplayName(
            "Should trigger daily yield recovery when the submitted task runs"
    )
    void shouldTriggerDailyYieldRecoveryWhenTaskRuns() {
        startupRecovery.recoverPendingYieldsOnStartup();

        verify(taskExecutor).execute(taskCaptor.capture());

        taskCaptor.getValue().run();

        verify(dailyYieldScheduler).processDailyYields();
        verifyNoMoreInteractions(dailyYieldScheduler);
    }

    @Test
    @DisplayName(
            "Should not propagate an unexpected failure from the recovery task"
    )
    void shouldNotPropagateUnexpectedFailure() {
        doThrow(new IllegalStateException("unexpected"))
                .when(dailyYieldScheduler)
                .processDailyYields();

        startupRecovery.recoverPendingYieldsOnStartup();

        verify(taskExecutor).execute(taskCaptor.capture());

        assertDoesNotThrow(() -> taskCaptor.getValue().run());

        verify(dailyYieldScheduler).processDailyYields();
    }
}