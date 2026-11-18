package com.lcsalvess.bankingsystem.repository.specification;

import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Provides reusable JPA specifications for filtering transaction records.
 *
 * <p>Supports filtering by account, transaction type, creation date range,
 * and an optional transaction ID boundary for consistent pagination snapshots.
 */
public final class TransactionSpecifications {

  private TransactionSpecifications() {
  }

  /**
   * Creates a transaction specification using account and optional filters.
   *
   * <p>Does not apply a transaction ID boundary.
   *
   * @param accountId the account identifier
   * @param type the optional transaction type filter
   * @param startDate the inclusive lower bound for the creation timestamp
   * @param endExclusive the exclusive upper bound for the creation timestamp
   * @return the specification matching the supplied filters
   */
  public static Specification<Transaction> byAccountAndFilters(
      Long accountId,
      TransactionType type,
      Instant startDate,
      Instant endExclusive
  ) {
    return byAccountAndFilters(
        accountId,
        type,
        startDate,
        endExclusive,
        null
    );
  }

  /**
   * Creates a transaction specification with an optional snapshot boundary.
   *
   * <p>Restricts results to transactions whose identifiers do not exceed
   * the specified maximum transaction ID.
   *
   * @param accountId the account identifier
   * @param type the optional transaction type filter
   * @param startDate the inclusive lower bound for the creation timestamp
   * @param endExclusive the exclusive upper bound for the creation timestamp
   * @param maxTransactionId the optional maximum transaction ID for the snapshot
   * @return the specification matching the supplied filters and snapshot boundary
   */
  public static Specification<Transaction> byAccountAndFilters(
      Long accountId,
      TransactionType type,
      Instant startDate,
      Instant endExclusive,
      Long maxTransactionId
  ) {
    return (root, query, cb) -> cb.and(
        cb.equal(root.get("account").get("id"), accountId),

        maxTransactionId == null
            ? cb.conjunction()
            : cb.lessThanOrEqualTo(
            root.get("id"),
            maxTransactionId
        ),

        type == null
            ? cb.conjunction()
            : cb.equal(root.get("type"), type),

        startDate == null
            ? cb.conjunction()
            : cb.greaterThanOrEqualTo(
            root.get("createdAt"),
            startDate
        ),

        endExclusive == null
            ? cb.conjunction()
            : cb.lessThan(
            root.get("createdAt"),
            endExclusive
        )
    );
  }
}
