package com.lcsalvess.bankingsystem.repository.audit;

import com.lcsalvess.bankingsystem.entity.audit.snapshot.AuditEventQuerySnapshot;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AuditEventQuerySnapshotRepository {

    private final EntityManager entityManager;

    public AuditEventQuerySnapshotRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Persists snapshot metadata and flushes it immediately.
     *
     * <p>Flushing ensures the parent snapshot exists before its items
     * are inserted, preserving the foreign key constraint.
     */
    public void save(AuditEventQuerySnapshot snapshot) {
        entityManager.persist(
                Objects.requireNonNull(snapshot, "The snapshot is required.")
        );

        entityManager.flush();
    }

    /**
     * Finds a snapshot belonging to the authenticated actor
     * that has not expired.
     */
    public Optional<AuditEventQuerySnapshot> findActiveByIdAndActor(
            UUID snapshotId,
            Long actorUserId,
            Instant now
    ) {
        List<AuditEventQuerySnapshot> snapshots = entityManager.createQuery(
                        """
                        SELECT snapshot
                        FROM AuditEventQuerySnapshot snapshot
                        WHERE snapshot.snapshotId = :snapshotId
                          AND snapshot.actorUserId = :actorUserId
                          AND snapshot.expiresAt > :now
                        """,
                        AuditEventQuerySnapshot.class
                )
                .setParameter("snapshotId", snapshotId)
                .setParameter("actorUserId", actorUserId)
                .setParameter("now", now)
                .setMaxResults(1)
                .getResultList();

        return snapshots.stream().findFirst();
    }

    /**
     * Removes the oldest active snapshots of an actor, keeping only the
     * {@code keep} most recent ones.
     *
     * <p>Called before a new snapshot is created, so the number of active
     * snapshots per actor stays bounded. The database foreign key cascades
     * the deletion to snapshot items.
     */
    public void deleteOldestActiveBeyond(
            Long actorUserId,
            Instant now,
            int keep
    ) {
        Objects.requireNonNull(actorUserId, "The actor ID is required.");
        Objects.requireNonNull(now, "The current instant is required.");

        if (keep < 0) {
            throw new IllegalArgumentException(
                    "The number of snapshots to keep cannot be negative."
            );
        }

        List<UUID> excessIds = entityManager.createQuery(
                        """
                        SELECT snapshot.snapshotId
                        FROM AuditEventQuerySnapshot snapshot
                        WHERE snapshot.actorUserId = :actorUserId
                          AND snapshot.expiresAt > :now
                        ORDER BY snapshot.createdAt DESC,
                                 snapshot.snapshotId DESC
                        """,
                        UUID.class
                )
                .setParameter("actorUserId", actorUserId)
                .setParameter("now", now)
                .setFirstResult(keep)
                .getResultList();

        if (excessIds.isEmpty()) {
            return;
        }

        entityManager.createQuery(
                        """
                        DELETE FROM AuditEventQuerySnapshot snapshot
                        WHERE snapshot.snapshotId IN :snapshotIds
                        """
                )
                .setParameter("snapshotIds", excessIds)
                .executeUpdate();
    }

    /**
     * Removes expired snapshots.
     *
     * <p>The database foreign key cascades the deletion to snapshot items.
     *
     * @return the number of deleted snapshots
     */
    public int deleteExpired(Instant now) {
        return entityManager.createQuery(
                        """
                        DELETE FROM AuditEventQuerySnapshot snapshot
                        WHERE snapshot.expiresAt <= :now
                        """
                )
                .setParameter("now", now)
                .executeUpdate();
    }
}
