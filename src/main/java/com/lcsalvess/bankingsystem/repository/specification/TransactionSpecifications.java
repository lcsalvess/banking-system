package com.lcsalvess.bankingsystem.repository.specification;

import com.lcsalvess.bankingsystem.entity.Transaction;
import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> byAccountAndFilters(
            Long accountId,
            TransactionType type,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("account").get("id"), accountId),
                type == null ? cb.conjunction() : cb.equal(root.get("type"), type),
                startDate == null ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(root.get("createdAt"), startDate),
                endDate == null ? cb.conjunction()
                        : cb.lessThanOrEqualTo(root.get("createdAt"), endDate)
        );
    }
}