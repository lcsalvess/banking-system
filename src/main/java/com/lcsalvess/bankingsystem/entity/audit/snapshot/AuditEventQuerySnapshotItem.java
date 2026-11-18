package com.lcsalvess.bankingsystem.entity.audit.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an audit event included in a query snapshot at a specific position.
 */
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

  /**
   * Creates a snapshot item after validating its audit event identifier.
   *
   * @param snapshotId the identifier of the query snapshot
   * @param position the item's position within the snapshot
   * @param auditEventId the identifier of the associated audit event
   * @return the validated snapshot item
   * @throws NullPointerException if the audit event ID is {@code null}
   * @throws IllegalArgumentException if the audit event ID is not positive
   */
  public static AuditEventQuerySnapshotItem create(
      UUID snapshotId,
      int position,
      Long auditEventId
  ) {
    AuditEventQuerySnapshotItemId validatedId =
        AuditEventQuerySnapshotItemId.create(snapshotId, position);

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
