package com.lcsalvess.bankingsystem.unit.service.transaction.idempotency;

import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionIdempotencyCleanupJob;
import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionIdempotencyCleanupStartupRecovery;
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
class TransactionIdempotencyCleanupStartupRecoveryTests {

    @Mock
    private TransactionIdempotencyCleanupJob cleanupJob;

    @Mock
    private TaskExecutor taskExecutor;

    @Captor
    private ArgumentCaptor<Runnable> taskCaptor;

    private TransactionIdempotencyCleanupStartupRecovery startupRecovery;

    @BeforeEach
    void setUp() {
        startupRecovery = new TransactionIdempotencyCleanupStartupRecovery(
                cleanupJob,
                taskExecutor
        );
    }

    @Test
    @DisplayName(
            "Should submit cleanup to the executor without running it on the calling thread"
    )
    void shouldSubmitCleanupWithoutRunningItOnCallingThread() {
        startupRecovery.cleanUpExpiredRequestsOnStartup();

        verify(taskExecutor).execute(taskCaptor.capture());
        verifyNoInteractions(cleanupJob);
    }

    @Test
    @DisplayName("Should trigger cleanup when the submitted task runs")
    void shouldTriggerCleanupWhenTaskRuns() {
        startupRecovery.cleanUpExpiredRequestsOnStartup();

        verify(taskExecutor).execute(taskCaptor.capture());

        taskCaptor.getValue().run();

        verify(cleanupJob).cleanUp();
        verifyNoMoreInteractions(cleanupJob);
    }

    @Test
    @DisplayName("Should not propagate unexpected failures from the cleanup task")
    void shouldNotPropagateUnexpectedFailure() {
        doThrow(new IllegalStateException("unexpected"))
                .when(cleanupJob)
                .cleanUp();

        startupRecovery.cleanUpExpiredRequestsOnStartup();

        verify(taskExecutor).execute(taskCaptor.capture());

        assertDoesNotThrow(() -> taskCaptor.getValue().run());

        verify(cleanupJob).cleanUp();
    }
}