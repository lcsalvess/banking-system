package com.lcsalvess.bankingsystem.repository.idempotency;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Provides database operations for transaction idempotency records.
 *
 * <p>Manages idempotency key reservations, transaction associations,
 * existing record retrieval, and expiration cleanup using JDBC.
 */
@Repository
public class TransactionIdempotencyRepository {

  private static final String INSERT_SQL =
      """
      INSERT INTO transaction_idempotency_requests (
          user_id,
          idempotency_key,
          operation_type,
          request_hash
      )
      VALUES (?, ?, ?, ?)
      ON CONFLICT (user_id, idempotency_key) DO NOTHING
      """;

  private static final String FIND_SQL =
      """
      SELECT
          idempotency_key,
          operation_type,
          request_hash,
          transaction_code
      FROM transaction_idempotency_requests
      WHERE user_id = ?
        AND idempotency_key = ?
      """;

  private static final String COMPLETE_SQL =
      """
      UPDATE transaction_idempotency_requests
      SET transaction_code = ?
      WHERE user_id = ?
        AND idempotency_key = ?
        AND transaction_code IS NULL
      """;

  /**
   * The {@code created_at} column represents an instant ({@code TIMESTAMPTZ}),
   * so retention is calculated directly using the database's current timestamp.
   */
  private static final String DELETE_OLDER_THAN_SQL =
      """
      DELETE FROM transaction_idempotency_requests
      WHERE created_at < CURRENT_TIMESTAMP
                         - make_interval(days => ?)
      """;

  private final JdbcTemplate jdbcTemplate;

  public TransactionIdempotencyRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Attempts to reserve an idempotency key for a transaction request.
   *
   * <p>Returns {@code true} if a new reservation is created or
   * {@code false} if the key is already reserved for the user.
   *
   * @param userId the identifier of the user requesting the operation
   * @param idempotencyKey the unique key identifying the request
   * @param operationType the type of financial operation
   * @param requestHash the fingerprint of the request payload
   * @return {@code true} if the reservation was created; {@code false} otherwise
   */
  public boolean tryReserve(
      long userId,
      UUID idempotencyKey,
      String operationType,
      String requestHash
  ) {
    // Explicit types are provided to ensure the PostgreSQL driver handles UUIDs correctly.
    int insertedRows = jdbcTemplate.update(
        INSERT_SQL,
        ps -> {
          ps.setLong(1, userId);
          ps.setObject(2, idempotencyKey, Types.OTHER);
          ps.setString(3, operationType);
          ps.setString(4, requestHash);
        }
    );

    return insertedRows == 1;
  }

  /**
   * Retrieves an idempotency record associated with a user and key.
   *
   * @param userId the identifier of the user who owns the reservation
   * @param idempotencyKey the unique key identifying the request
   * @return an optional containing the matching idempotency record,
   *     or empty if no record exists
   */
  public Optional<TransactionIdempotencyRecord> findByUserIdAndKey(
      long userId,
      UUID idempotencyKey
  ) {
    return jdbcTemplate.query(
        FIND_SQL,
        ps -> {
          ps.setLong(1, userId);
          ps.setObject(2, idempotencyKey, Types.OTHER);
        },
        (rs, rowNum) -> new TransactionIdempotencyRecord(
            rs.getObject("idempotency_key", UUID.class),
            rs.getString("operation_type"),
            rs.getString("request_hash"),
            rs.getObject("transaction_code", UUID.class)
        )
    ).stream().findFirst();
  }

  /**
   * Associates a reserved idempotency record with its resulting transaction.
   *
   * <p>Updates only records that have not yet been associated with a
   * transaction.
   *
   * @param userId the identifier of the user who owns the reservation
   * @param idempotencyKey the unique key identifying the request
   * @param transactionCode the identifier of the resulting transaction
   * @throws IllegalStateException if the record does not exist or
   *     has already been completed
   */
  public void complete(
      long userId,
      UUID idempotencyKey,
      UUID transactionCode
  ) {
    int updatedRows = jdbcTemplate.update(
        COMPLETE_SQL,
        ps -> {
          ps.setObject(1, transactionCode, Types.OTHER);
          ps.setLong(2, userId);
          ps.setObject(3, idempotencyKey, Types.OTHER);
        }
    );

    if (updatedRows != 1) {
      throw new IllegalStateException(
          "Failed to complete the idempotency record. "
              + "The record may not exist or may have already been completed."
      );
    }
  }

  /**
   * Deletes idempotency records older than the specified retention period.
   *
   * <p>Calculates the retention cutoff using the database's current timestamp.
   *
   * @param retentionDays the record retention period in days
   * @return the number of deleted records
   */
  public int deleteOlderThanDays(int retentionDays) {
    return jdbcTemplate.update(DELETE_OLDER_THAN_SQL, retentionDays);
  }
}
