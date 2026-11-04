package com.lcsalvess.bankingsystem.entity.audit.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_event_query_snapshot_items")
public class AuditEventQuerySnapshotItem {

    @EmbeddedId
    private AuditEventQuerySnapshotItemId id;

    @Column(name = "audit_event_id", nullable = false, updatable = false)
    private Long auditEventId;

    /**
     * Required by JPA.
     */
    protected AuditEventQuerySnapshotItem() {
    }

    /**
     * Performs field assignment only.
     * <p>
     * Validation and embedded ID creation must be completed by the factory
     * before this constructor is called.
     */
    private AuditEventQuerySnapshotItem(
            AuditEventQuerySnapshotItemId id,
            Long auditEventId
    ) {
        this.id = id;
        this.auditEventId = auditEventId;
    }

    public static AuditEventQuerySnapshotItem create(
            UUID snapshotId,
            int position,
            Long auditEventId
    ) {
        AuditEventQuerySnapshotItemId validatedId =
                AuditEventQuerySnapshotItemId.create(
                        snapshotId,
                        position
                );

        Long validatedAuditEventId = Objects.requireNonNull(
                auditEventId,
                "The audit event ID is required."
        );

        if (validatedAuditEventId <= 0) {
            throw new IllegalArgumentException(
                    "The audit event ID must be positive."
            );
        }

        return new AuditEventQuerySnapshotItem(
                validatedId,
                validatedAuditEventId
        );
    }

    public AuditEventQuerySnapshotItemId getId() {
        return id;
    }

    public Long getAuditEventId() {
        return auditEventId;
    }
}
