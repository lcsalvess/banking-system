package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.repository.projection.BalanceChainBreak;
import com.lcsalvess.bankingsystem.repository.projection.BalanceMismatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends
        JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTransactionCode(UUID transactionCode);

    Optional<Transaction> findFirstByAccount_IdOrderByIdDesc(
            Long accountId
    );

    Optional<Transaction> findByTransactionCodeAndAccount_Id(
            UUID transactionCode,
            Long accountId
    );

    boolean existsByAccountIdAndTypeAndYieldDate(
            Long accountId,
            TransactionType type,
            LocalDate yieldDate
    );

    @Query(value = """
        SELECT COALESCE(
            SUM(
                CASE
                    WHEN transaction_type IN (
                        'DEPOSIT',
                        'TRANSFER_RECEIVED',
                        'YIELD'
                    )
                        THEN amount

                    WHEN transaction_type IN (
                        'WITHDRAWAL',
                        'TRANSFER_SENT'
                    )
                        THEN -amount

                    ELSE 0
                END
            ),
            0
        )
        FROM transactions
        WHERE account_id = :accountId
          AND (
              (
                  transaction_type = 'YIELD'
                  AND yield_date < :referenceDate
              )
              OR
              (
                  transaction_type <> 'YIELD'
                  AND created_at < :dayStart
              )
          )
        """, nativeQuery = true)
    BigDecimal calculateBalanceAtStartOfDay(
            @Param("accountId") Long accountId,
            @Param("referenceDate") LocalDate referenceDate,
            @Param("dayStart") Instant dayStart
    );

    /**
     * Retrieves the opening balance for the day from {@code balance_after},
     * without summing the transaction history.
     *
     * <p>The candidate is the latest transaction (by ID) that contributes to
     * the opening balance for the day, meaning a non-yield transaction created
     * before the day or a yield transaction with a reference date before the day.
     *
     * <p>Its {@code balance_after} is valid only if no transaction that does
     * NOT contribute to the opening balance (effective as of the day) has a
     * lower ID. Otherwise, the physical balance after the candidate would
     * include transactions that must not be included in the opening balance.
     * This can happen when a retroactive yield is recorded after newer transactions.
     *
     * <p>In that case, or when no transaction history exists, the query returns
     * no row, and the caller must use {@link #calculateBalanceAtStartOfDay}.
     *
     * <p>Must be called while the account row is locked.
     */
    @Query(value = """
        SELECT c.balance_after
        FROM (
            SELECT t.id, t.balance_after
            FROM transactions t
            WHERE t.account_id = :accountId
              AND (
                  (t.transaction_type = 'YIELD'
                      AND t.yield_date < :referenceDate)
                  OR
                  (t.transaction_type <> 'YIELD'
                      AND t.created_at < :dayStart)
              )
            ORDER BY t.id DESC
            LIMIT 1
        ) c
        WHERE NOT EXISTS (
            SELECT 1
            FROM transactions o
            WHERE o.account_id = :accountId
              AND o.id < c.id
              AND (
                  (o.transaction_type = 'YIELD'
                      AND o.yield_date >= :referenceDate)
                  OR
                  (o.transaction_type <> 'YIELD'
                      AND o.created_at >= :dayStart)
              )
        )
        """, nativeQuery = true)
    Optional<BigDecimal> findBalanceAtStartOfDayFromSnapshot(
            @Param("accountId") Long accountId,
            @Param("referenceDate") LocalDate referenceDate,
            @Param("dayStart") Instant dayStart
    );

    /**
     * Identifies accounts whose balance differs from the {@code balance_after}
     * value of their latest transaction (or from zero if no transaction exists).
     *
     * <p>Since this is a single statement, it sees a consistent snapshot of
     * accounts and transactions.
     */
    @Query(value = """
        SELECT m."accountId" AS "accountId",
               m."accountNumber" AS "accountNumber",
               m."accountBalance" AS "accountBalance",
               m."ledgerBalance" AS "ledgerBalance"
        FROM (
            SELECT a.id AS "accountId",
                   a.account_number AS "accountNumber",
                   a.balance AS "accountBalance",
                   COALESCE(
                       (
                           SELECT t.balance_after
                           FROM transactions t
                           WHERE t.account_id = a.id
                           ORDER BY t.id DESC
                           LIMIT 1
                       ),
                       0
                   ) AS "ledgerBalance"
            FROM accounts a
        ) m
        WHERE m."accountBalance" <> m."ledgerBalance"
        ORDER BY m."accountId"
        """, nativeQuery = true)
    List<BalanceMismatch> findBalanceMismatches();

    /**
     * Identifies the first break in each account's historical balance chain.
     *
     * <p>The first transaction is validated against the initial zero balance.
     * Subsequent transactions are validated against the previous transaction's balance.
     */
    @Query(value = """
    WITH transaction_chain AS (
        SELECT
            t.account_id,
            t.id,
            t.balance_after,
            t.amount,
            t.transaction_type,
            LAG(t.balance_after, 1, CAST(0 AS numeric)) OVER (
                PARTITION BY t.account_id
                ORDER BY t.id
            ) AS previous_balance
        FROM transactions t
    )
    SELECT
        account_id AS "accountId",
        MIN(id) AS "transactionId"
    FROM transaction_chain
    WHERE balance_after <> previous_balance
        + CASE
            WHEN transaction_type IN (
                'DEPOSIT',
                'TRANSFER_RECEIVED',
                'YIELD'
            ) THEN amount
            ELSE -amount
        END
    GROUP BY account_id
    ORDER BY account_id
    """, nativeQuery = true)
    List<BalanceChainBreak> findBalanceChainBreaks();
}
