package com.lcsalvess.bankingsystem.repository.specification;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.AccountType;
import org.springframework.data.jpa.domain.Specification;

public final class AccountSpecifications {

    private AccountSpecifications() {
    }

    /**
     * Accounts that belong to the given client, optionally narrowed by type
     * and status. A null filter is ignored.
     */
    public static Specification<Account> byClientAndFilters(
            Long clientId,
            AccountType type,
            AccountStatus status
    ) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("client").get("id"), clientId),
                type == null
                        ? cb.conjunction()
                        : cb.equal(root.get("type"), type),
                status == null
                        ? cb.conjunction()
                        : cb.equal(root.get("status"), status)
        );
    }
}
