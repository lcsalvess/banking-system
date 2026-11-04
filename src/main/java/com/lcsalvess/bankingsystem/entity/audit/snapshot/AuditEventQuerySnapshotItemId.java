package com.lcsalvess.bankingsystem.entity.audit.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AuditEventQuerySnapshotItemId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(name = "position", nullable = false, updatable = false)
    private Integer position;

    /**
     * Required by JPA.
     */
    protected AuditEventQuerySnapshotItemId() {
    }

    /**
     * Performs field assignment only.
     * <p>
     * All validation must be completed by the factory before
     * this constructor is called.
     */
    private AuditEventQuerySnapshotItemId(
            UUID snapshotId,
            Integer position
    ) {
        this.snapshotId = snapshotId;
        this.position = position;
    }

    public static AuditEventQuerySnapshotItemId create(
            UUID snapshotId,
            int position
    ) {
        UUID validatedSnapshotId = Objects.requireNonNull(
                snapshotId,
                "The snapshot ID is required."
        );

        if (position < 0) {
            throw new IllegalArgumentException(
                    "The snapshot item position cannot be negative."
            );
        }

        return new AuditEventQuerySnapshotItemId(
                validatedSnapshotId,
                position
        );
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public Integer getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof AuditEventQuerySnapshotItemId that)) {
            return false;
        }

        return Objects.equals(snapshotId, that.snapshotId)
                && Objects.equals(position, that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotId, position);
    }
}
