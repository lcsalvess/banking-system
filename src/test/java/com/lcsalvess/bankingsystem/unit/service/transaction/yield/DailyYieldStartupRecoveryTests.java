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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DailyYieldStartupRecoveryTests {

    @Mock
    private DailyYieldScheduler dailyYieldScheduler;

    @Mock
    private Executor executor;

    @Captor
    private ArgumentCaptor<Runnable> taskCaptor;

    private DailyYieldStartupRecovery startupRecovery;

    @BeforeEach
    void setUp() {
        startupRecovery = new DailyYieldStartupRecovery(
                dailyYieldScheduler,
                executor
        );
    }

    @Test
    @DisplayName("Should submit the recovery to the executor without running it on the calling thread")
    void shouldSubmitRecoveryWithoutRunningItOnCallingThread() {
        startupRecovery.recoverPendingYieldsOnStartup();

        verify(executor).execute(taskCaptor.capture());
        verifyNoInteractions(dailyYieldScheduler);
    }

    @Test
    @DisplayName("Should trigger daily yield recovery when the submitted task runs")
    void shouldTriggerDailyYieldRecoveryWhenTaskRuns() {
        startupRecovery.recoverPendingYieldsOnStartup();

        verify(executor).execute(taskCaptor.capture());

        taskCaptor.getValue().run();

        verify(dailyYieldScheduler).processDailyYields();
        verifyNoMoreInteractions(dailyYieldScheduler);
    }

    @Test
    @DisplayName("Should not propagate an unexpected failure from the recovery task")
    void shouldNotPropagateUnexpectedFailure() {
        doThrow(new IllegalStateException("unexpected"))
                .when(dailyYieldScheduler)
                .processDailyYields();

        startupRecovery.recoverPendingYieldsOnStartup();

        verify(executor).execute(taskCaptor.capture());

        assertDoesNotThrow(() -> taskCaptor.getValue().run());

        verify(dailyYieldScheduler).processDailyYields();
    }

    @Test
    @DisplayName("Should run the recovery on a dedicated thread by default")
    void shouldRunRecoveryOnDedicatedThreadByDefault() throws Exception {
        CompletableFuture<Thread> executionThread = new CompletableFuture<>();

        doAnswer(invocation -> {
            executionThread.complete(Thread.currentThread());
            return null;
        }).when(dailyYieldScheduler).processDailyYields();

        DailyYieldStartupRecovery defaultRecovery =
                new DailyYieldStartupRecovery(dailyYieldScheduler);

        defaultRecovery.recoverPendingYieldsOnStartup();

        Thread thread = executionThread.get(5, TimeUnit.SECONDS);

        assertNotSame(Thread.currentThread(), thread);
        assertEquals(DailyYieldStartupRecovery.THREAD_NAME, thread.getName());
    }
}