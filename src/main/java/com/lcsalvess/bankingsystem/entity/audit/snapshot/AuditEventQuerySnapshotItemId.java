package com.lcsalvess.bankingsystem.entity.audit.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the composite identifier of an audit event query snapshot item.
 */
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

  /**
   * Creates a snapshot item identifier after validating its components.
   *
   * @param snapshotId the identifier of the query snapshot
   * @param position the item's position within the snapshot
   * @return the validated composite identifier
   * @throws NullPointerException if the snapshot ID is {@code null}
   * @throws IllegalArgumentException if the position is negative
   */
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

  /**
   * Determines whether another object has the same snapshot ID and position.
   *
   * @param object the object to compare with this identifier
   * @return {@code true} if both identifiers have equal components;
   *         {@code false} otherwise
   */
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

  /**
   * Computes the hash code from the snapshot ID and position.
   *
   * @return the hash code of this composite identifier
   */
  @Override
  public int hashCode() {
    return Objects.hash(snapshotId, position);
  }
}
