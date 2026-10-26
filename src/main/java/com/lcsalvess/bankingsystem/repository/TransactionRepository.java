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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends
        JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTransactionCode(UUID transactionCode);

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
            @Param("dayStart") LocalDateTime dayStart
    );

    /**
     * Saldo no início do dia lido do balance_after, sem somar o histórico.
     *
     * <p>Candidata: última transação (por id) que conta para o saldo do início
     * do dia, isto é, não-rendimento criada antes do dia ou rendimento com
     * data de referência anterior ao dia. O balance_after dela só vale se
     * nenhuma transação que NÃO conta (efetiva a partir do dia) tiver id
     * menor, porque então o saldo físico após a candidata inclui movimentos
     * que o saldo do início do dia não deve incluir. Isso acontece quando um
     * rendimento retroativo é gravado depois de movimentações mais novas.
     * Nesse caso, ou sem histórico, a consulta não retorna linha e o chamador
     * deve usar {@link #calculateBalanceAtStartOfDay}.
     *
     * <p>Deve ser chamada com a linha da conta travada.
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
            @Param("dayStart") LocalDateTime dayStart
    );

    /**
     * Contas cujo saldo difere do balance_after da última transação (ou de
     * zero, se não houver transação). Uma única instrução, então enxerga um
     * snapshot consistente de contas e transações.
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
     * Identifica a primeira quebra da cadeia de saldos históricos de cada conta.
     *
     * <p>A primeira transação é validada contra o saldo inicial zero.
     * As transações seguintes são validadas contra o saldo da transação anterior.
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
