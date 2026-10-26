package com.lcsalvess.bankingsystem.unit.service.transaction.balancereconciliation;

import com.lcsalvess.bankingsystem.service.transaction.balancereconciliation.BalanceReconciliationJob;
import com.lcsalvess.bankingsystem.service.transaction.balancereconciliation.BalanceReconciliationStartupRecovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceReconciliationStartupRecoveryTests {

    @Mock
    private BalanceReconciliationJob balanceReconciliationJob;

    private BalanceReconciliationStartupRecovery startupRecovery;

    @BeforeEach
    void setUp() {
        startupRecovery = new BalanceReconciliationStartupRecovery(
                balanceReconciliationJob,
                new SyncTaskExecutor()
        ) {
            @Override
            protected boolean waitBeforeRetry(long delayMillis) {
                return true;
            }
        };
    }

    @Test
    void shouldRunReconciliationOnceWhenStartupExecutionSucceeds() {
        when(balanceReconciliationJob.reconcile()).thenReturn(0);

        startupRecovery.onApplicationReady();

        verify(balanceReconciliationJob, times(1)).reconcile();
    }

    @Test
    void shouldRetryWhenFirstStartupAttemptFails() {
        when(balanceReconciliationJob.reconcile())
                .thenThrow(new IllegalStateException("Temporary database failure"))
                .thenReturn(0);

        startupRecovery.onApplicationReady();

        verify(balanceReconciliationJob, times(2)).reconcile();
    }

    @Test
    void shouldStopAfterMaximumAttemptsWhenEveryAttemptFails() {
        when(balanceReconciliationJob.reconcile())
                .thenThrow(new IllegalStateException("Database unavailable"));

        startupRecovery.onApplicationReady();

        verify(balanceReconciliationJob, times(3)).reconcile();
    }

    @Test
    void shouldNotRetryWhenReconciliationFindsAffectedAccounts() {
        when(balanceReconciliationJob.reconcile()).thenReturn(2);

        startupRecovery.onApplicationReady();

        verify(balanceReconciliationJob, times(1)).reconcile();
    }
}
