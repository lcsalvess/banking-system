package com.lucas.bankingsystem.repository;

import com.lucas.bankingsystem.entity.Transaction;
import com.lucas.bankingsystem.entity.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);

    Optional<Transaction> findByTransactionCode(UUID transactionCode);

    boolean existsByAccountIdAndTypeAndCreatedAtBetween(
            Long accountId,
            TransactionType type,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay
    );
}
