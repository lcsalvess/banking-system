package com.lcsalvess.bankingsystem.entity.audit.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores the metadata and validation hashes of an audit event query snapshot.
 */
@Entity
@Table(name = "audit_event_query_snapshots")
public class AuditEventQuerySnapshot {

  public static final int MAX_TOTAL_ELEMENTS = 10_000;
  public static final int HASH_LENGTH = 32;

  @Id
  @Column(name = "snapshot_id", nullable = false, updatable = false)
  private UUID snapshotId;

  @Column(name = "actor_user_id", nullable = false, updatable = false)
  private Long actorUserId;

  @Column(
      name = "filter_hash",
      nullable = false,
      updatable = false,
      columnDefinition = "bytea"
  )
  private byte[] filterHash;

  @Column(
      name = "sort_hash",
      nullable = false,
      updatable = false,
      columnDefinition = "bytea"
  )
  private byte[] sortHash;

  @Column(name = "total_elements", nullable = false, updatable = false)
  private int totalElements;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  /**
   * Required by JPA.
   */
  protected AuditEventQuerySnapshot() {
  }

  /**
   * Performs field assignment only.
   * <p>
   * All validation and defensive copying must be completed by the factory
   * before this constructor is called.
   */
  private AuditEventQuerySnapshot(
      UUID snapshotId,
      Long actorUserId,
      byte[] filterHash,
      byte[] sortHash,
      int totalElements,
      Instant createdAt,
      Instant expiresAt
  ) {
    this.snapshotId = snapshotId;
    this.actorUserId = actorUserId;
    this.filterHash = filterHash;
    this.sortHash = sortHash;
    this.totalElements = totalElements;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  /**
   * Creates a snapshot after validating its metadata and defensively copying its hashes.
   *
   * @param snapshotId the unique identifier of the snapshot
   * @param actorUserId the identifier of the user who owns the snapshot
   * @param filterHash the SHA-256 hash of the query filters
   * @param sortHash the SHA-256 hash of the query sorting configuration
   * @param totalElements the total number of elements included in the snapshot
   * @param createdAt the snapshot creation timestamp
   * @param expiresAt the snapshot expiration timestamp
   * @return the validated audit event query snapshot
   * @throws NullPointerException if a required argument is {@code null}
   * @throws IllegalArgumentException if an argument violates the snapshot constraints
   */
  public static AuditEventQuerySnapshot create(
      UUID snapshotId,
      Long actorUserId,
      byte[] filterHash,
      byte[] sortHash,
      int totalElements,
      Instant createdAt,
      Instant expiresAt
  ) {
    final UUID validatedSnapshotId = Objects.requireNonNull(
        snapshotId,
        "The snapshot ID is required."
    );

    Long validatedActorUserId = Objects.requireNonNull(
        actorUserId,
        "The snapshot actor ID is required."
    );

    if (validatedActorUserId <= 0) {
      throw new IllegalArgumentException(
          "The snapshot actor ID must be positive."
      );
    }

    byte[] validatedFilterHash = validateHash(
        filterHash,
        "filterHash"
    );

    byte[] validatedSortHash = validateHash(
        sortHash,
        "sortHash"
    );

    if (totalElements < 0 || totalElements > MAX_TOTAL_ELEMENTS) {
      throw new IllegalArgumentException(
          "The snapshot total elements must be between 0 and "
              + MAX_TOTAL_ELEMENTS + "."
      );
    }

    Instant validatedCreatedAt = Objects.requireNonNull(
        createdAt,
        "The snapshot creation timestamp is required."
    );

    Instant validatedExpiresAt = Objects.requireNonNull(
        expiresAt,
        "The snapshot expiration timestamp is required."
    );

    if (!validatedExpiresAt.isAfter(validatedCreatedAt)) {
      throw new IllegalArgumentException(
          "The snapshot expiration must be after its creation."
      );
    }

    return new AuditEventQuerySnapshot(
        validatedSnapshotId,
        validatedActorUserId,
        validatedFilterHash,
        validatedSortHash,
        totalElements,
        validatedCreatedAt,
        validatedExpiresAt
    );
  }

  private static byte[] validateHash(byte[] hash, String fieldName) {
    Objects.requireNonNull(
        hash,
        "The " + fieldName + " is required."
    );

    if (hash.length != HASH_LENGTH) {
      throw new IllegalArgumentException(
          "The " + fieldName + " must contain exactly "
              + HASH_LENGTH + " bytes."
      );
    }

    return hash.clone();
  }

  public UUID getSnapshotId() {
    return snapshotId;
  }

  public Long getActorUserId() {
    return actorUserId;
  }

  public byte[] getFilterHash() {
    return filterHash.clone();
  }

  public byte[] getSortHash() {
    return sortHash.clone();
  }

  public int getTotalElements() {
    return totalElements;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
