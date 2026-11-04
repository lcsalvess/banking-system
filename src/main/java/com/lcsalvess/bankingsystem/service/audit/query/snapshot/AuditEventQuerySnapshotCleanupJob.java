package com.lcsalvess.bankingsystem.service.audit.query.snapshot;

import com.lcsalvess.bankingsystem.repository.audit.AuditEventQuerySnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class AuditEventQuerySnapshotCleanupJob {

    private static final Logger log =
            LoggerFactory.getLogger(AuditEventQuerySnapshotCleanupJob.class);

    private final AuditEventQuerySnapshotRepository snapshotRepository;
    private final Clock clock;

    /**
     * Creates the cleanup job after validating its dependencies.
     */
    public static AuditEventQuerySnapshotCleanupJob create(
            AuditEventQuerySnapshotRepository snapshotRepository,
            Clock clock
    ) {
        Objects.requireNonNull(
                snapshotRepository,
                "The audit query snapshot repository is required."
        );

        Objects.requireNonNull(
                clock,
                "The application clock is required."
        );

        return new AuditEventQuerySnapshotCleanupJob(
                snapshotRepository,
                clock
        );
    }

    /**
     * Protected constructor allows Spring CGLIB to create a transactional proxy.
     */
    protected AuditEventQuerySnapshotCleanupJob(
            AuditEventQuerySnapshotRepository snapshotRepository,
            Clock clock
    ) {
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
    }

    /**
     * Removes expired audit query snapshots.
     *
     * <p>The transaction is required because the repository uses
     * Propagation.MANDATORY.
     *
     * <p>Dependent snapshot items are removed by PostgreSQL through
     * the ON DELETE CASCADE foreign key.
     */
    @Scheduled(
            cron = "${banking.audit.snapshot.cleanup-cron:0 */5 * * * *}",
            zone = "America/Sao_Paulo"
    )
    @Transactional
    public void cleanUp() {
        Instant now = clock.instant();

        int deletedSnapshots = snapshotRepository.deleteExpired(now);

        if (deletedSnapshots > 0) {
            log.info(
                    "Audit query snapshot cleanup finished: "
                            + "deletedSnapshots={}, cleanupTime={}",
                    deletedSnapshots,
                    now
            );
        } else {
            log.debug(
                    "Audit query snapshot cleanup found nothing to delete: "
                            + "cleanupTime={}",
                    now
            );
        }
    }
}
