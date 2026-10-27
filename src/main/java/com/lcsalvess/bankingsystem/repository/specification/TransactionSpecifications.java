package com.lcsalvess.bankingsystem.repository.specification;

import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> byAccountAndFilters(
            Long accountId,
            TransactionType type,
            Instant startDate,
            Instant endExclusive
    ) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("account").get("id"), accountId),
                type == null ? cb.conjunction() : cb.equal(root.get("type"), type),
                startDate == null ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        startDate
                ),
                endExclusive == null ? cb.conjunction()
                        : cb.lessThan(
                        root.get("createdAt"),
                        endExclusive
                )
        );
    }
}