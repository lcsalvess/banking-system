package com.lcsalvess.bankingsystem.repository;

import com.lcsalvess.bankingsystem.entity.Transaction;
import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

}
