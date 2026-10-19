package com.lcsalvess.bankingsystem.unit.util.pagination;

import com.lcsalvess.bankingsystem.util.pagination.PageableTieBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("Pageable tie-breaker")
class PageableTieBreakerTests {

    @Nested
    @DisplayName("When pageable is paged")
    class PagedPageableTests {

        @Test
        @DisplayName("Should append id ascending after the requested sort")
        void shouldAppendIdAfterRequestedSort() {
            Pageable result = PageableTieBreaker.withIdTieBreaker(
                    PageRequest.of(
                            2,
                            25,
                            Sort.by(
                                    Sort.Order.desc("balance"),
                                    Sort.Order.asc("type")
                            )
                    )
            );

            assertEquals(
                    Sort.by(
                            Sort.Order.desc("balance"),
                            Sort.Order.asc("type"),
                            Sort.Order.asc("id")
                    ),
                    result.getSort()
            );
            assertEquals(2, result.getPageNumber());
            assertEquals(25, result.getPageSize());
        }

        @Test
        @DisplayName("Should sort by id when the request has no sort")
        void shouldSortByIdWhenRequestIsUnsorted() {
            Pageable result = PageableTieBreaker.withIdTieBreaker(
                    PageRequest.of(0, 10)
            );

            assertEquals(
                    Sort.by(Sort.Order.asc("id")),
                    result.getSort()
            );
        }

        @Test
        @DisplayName("Should keep the pageable when id is already sorted")
        void shouldKeepPageableWhenIdIsAlreadySorted() {
            Pageable pageable = PageRequest.of(
                    0,
                    10,
                    Sort.by(
                            Sort.Order.desc("createdAt"),
                            Sort.Order.desc("id")
                    )
            );

            assertSame(
                    pageable,
                    PageableTieBreaker.withIdTieBreaker(pageable)
            );
        }
    }

    @Nested
    @DisplayName("When pageable is unpaged")
    class UnpagedPageableTests {

        @Test
        @DisplayName("Should keep the unpaged pageable unchanged")
        void shouldKeepUnpagedPageable() {
            Pageable pageable = Pageable.unpaged();

            assertSame(
                    pageable,
                    PageableTieBreaker.withIdTieBreaker(pageable)
            );
        }
    }
}