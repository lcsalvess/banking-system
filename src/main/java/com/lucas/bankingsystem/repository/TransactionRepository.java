package com.lucas.bankingsystem.repository;

import com.lucas.bankingsystem.entity.Transaction;
import com.lucas.bankingsystem.entity.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountId(Long accountId);

    boolean existsByAccountIdAndTypeAndCreatedAtBetween(
            Long accountId,
            TransactionType type,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay
    );
}
