package com.lcsalvess.bankingsystem.repository.audit;

import com.lcsalvess.bankingsystem.entity.audit.snapshot.AuditEventQuerySnapshotItem;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AuditEventQuerySnapshotItemRepository {

    private static final int BATCH_SIZE = 500;

    private final EntityManager entityManager;

    public AuditEventQuerySnapshotItemRepository(
            EntityManager entityManager
    ) {
        this.entityManager = entityManager;
    }

    /**
     * Persists event IDs with their original positions in the snapshot.
     *
     * <p>Items are inserted in bounded JDBC batches to avoid executing
     * one database round trip per event.
     *
     * @param snapshotId the snapshot identifier
     * @param auditEventIds event IDs in their original query order
     */
    public void saveAll(UUID snapshotId, List<Long> auditEventIds) {
        Objects.requireNonNull(snapshotId, "The snapshot ID is required.");
        Objects.requireNonNull(auditEventIds, "The event IDs are required.");

        for (int position = 0; position < auditEventIds.size(); position++) {
            AuditEventQuerySnapshotItem item =
                    AuditEventQuerySnapshotItem.create(
                            snapshotId,
                            position,
                            auditEventIds.get(position)
                    );

            entityManager.persist(item);

            if ((position + 1) % BATCH_SIZE == 0) {
                entityManager.flush();
            }
        }

        entityManager.flush();
    }

    /**
     * Retrieves event IDs ordered by their materialized positions.
     *
     * @param snapshotId the snapshot identifier
     * @param offset the first position to retrieve
     * @param pageSize maximum number of IDs to retrieve
     */
    public List<Long> findEventIds(
            UUID snapshotId,
            int offset,
            int pageSize
    ) {
        Objects.requireNonNull(snapshotId, "The snapshot ID is required.");

        if (offset < 0) {
            throw new IllegalArgumentException(
                    "The snapshot offset cannot be negative."
            );
        }

        if (pageSize < 1) {
            throw new IllegalArgumentException(
                    "The snapshot page size must be positive."
            );
        }

        return entityManager.createQuery(
                        """
                        SELECT item.auditEventId
                        FROM AuditEventQuerySnapshotItem item
                        WHERE item.id.snapshotId = :snapshotId
                        ORDER BY item.id.position ASC
                        """,
                        Long.class
                )
                .setParameter("snapshotId", snapshotId)
                .setFirstResult(offset)
                .setMaxResults(pageSize)
                .getResultList();
    }
}
