package com.lcsalvess.bankingsystem.unit.service.audit.query.snapshot;

import com.lcsalvess.bankingsystem.repository.audit.AuditEventQuerySnapshotRepository;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotCleanupJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEventQuerySnapshotCleanupJob tests")
class AuditEventQuerySnapshotCleanupJobTests {

    private static final Instant NOW =
            Instant.parse("2026-11-03T20:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private AuditEventQuerySnapshotRepository snapshotRepository;

    private AuditEventQuerySnapshotCleanupJob cleanupJob;

    @BeforeEach
    void setUp() {
        cleanupJob = AuditEventQuerySnapshotCleanupJob.create(
                snapshotRepository,
                CLOCK
        );
    }

    @Test
    @DisplayName("Should delete expired snapshots using the application clock")
    void shouldDeleteExpiredSnapshots() {
        when(snapshotRepository.deleteExpired(NOW))
                .thenReturn(3);

        cleanupJob.cleanUp();

        verify(snapshotRepository).deleteExpired(NOW);
        verifyNoMoreInteractions(snapshotRepository);
    }

    @Test
    @DisplayName("Should complete successfully when no snapshots have expired")
    void shouldCompleteWhenNoSnapshotsHaveExpired() {
        when(snapshotRepository.deleteExpired(NOW))
                .thenReturn(0);

        cleanupJob.cleanUp();

        verify(snapshotRepository).deleteExpired(NOW);
        verifyNoMoreInteractions(snapshotRepository);
    }

    @Test
    @DisplayName("Should propagate repository failures")
    void shouldPropagateRepositoryFailures() {
        when(snapshotRepository.deleteExpired(NOW))
                .thenThrow(new IllegalStateException("Database unavailable"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> cleanupJob.cleanUp()
        );

        assertEquals("Database unavailable", exception.getMessage());

        verify(snapshotRepository).deleteExpired(NOW);
        verifyNoMoreInteractions(snapshotRepository);
    }

    @Test
    @DisplayName("Should reject a null repository")
    void shouldRejectNullRepository() {
        assertThrows(
                NullPointerException.class,
                () -> AuditEventQuerySnapshotCleanupJob.create(
                        null,
                        CLOCK
                )
        );
    }

    @Test
    @DisplayName("Should reject a null clock")
    void shouldRejectNullClock() {
        assertThrows(
                NullPointerException.class,
                () -> AuditEventQuerySnapshotCleanupJob.create(
                        snapshotRepository,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should declare the configured scheduled cleanup")
    void shouldDeclareScheduledCleanup() throws NoSuchMethodException {
        Scheduled scheduled = AuditEventQuerySnapshotCleanupJob.class
                .getMethod("cleanUp")
                .getAnnotation(Scheduled.class);

        assertEquals(
                "${banking.audit.snapshot.cleanup-cron:0 */5 * * * *}",
                scheduled.cron()
        );

        assertEquals(
                "America/Sao_Paulo",
                scheduled.zone()
        );
    }
}
