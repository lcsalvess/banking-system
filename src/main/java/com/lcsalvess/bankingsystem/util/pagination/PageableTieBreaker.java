package com.lcsalvess.bankingsystem.util.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Guarantees a total ordering: without a unique trailing criterion, sorting by
 * columns with ties (role, active, balance, type, amount...) makes records
 * repeat or disappear between pages.
 */
public final class PageableTieBreaker {

    private static final String ID_PROPERTY = "id";

    private PageableTieBreaker() {
    }

    public static Pageable withIdTieBreaker(Pageable pageable) {
        return withTieBreaker(pageable, ID_PROPERTY);
    }

    public static Pageable withTieBreaker(Pageable pageable, String property) {
        if (pageable.isUnpaged()
                || pageable.getSort().getOrderFor(property) != null) {
            return pageable;
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(
                        Sort.by(Sort.Direction.ASC, property)
                )
        );
    }
}
