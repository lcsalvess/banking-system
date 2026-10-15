package com.lcsalvess.bankingsystem.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Garante ordenação total: sem um critério único no final, ordenar por colunas
 * com empates (role, active, balance, type, amount...) faz registros repetirem
 * ou sumirem entre páginas.
 */
public final class PageableTieBreaker {

    private static final String TIE_BREAKER_PROPERTY = "id";

    private PageableTieBreaker() {
    }

    public static Pageable withIdTieBreaker(Pageable pageable) {
        if (pageable.isUnpaged()
                || pageable.getSort().getOrderFor(TIE_BREAKER_PROPERTY) != null) {
            return pageable;
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(
                        Sort.by(Sort.Direction.ASC, TIE_BREAKER_PROPERTY)
                )
        );
    }
}