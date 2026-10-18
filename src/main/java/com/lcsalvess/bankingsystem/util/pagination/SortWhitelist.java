package com.lcsalvess.bankingsystem.util.pagination;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.pagination.InvalidSortPropertyException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class SortWhitelist {

    private SortWhitelist() {
    }

    public static void validate(Pageable pageable, Set<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new InvalidSortPropertyException(
                        ApiErrorMessages.INVALID_SORT_PROPERTY
                );
            }
        }
    }
}