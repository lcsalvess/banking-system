package com.lcsalvess.bankingsystem.unit.util.pagination;

import com.lcsalvess.bankingsystem.util.pagination.PageableTieBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pageable tie-breaker")
class PageableTieBreakerTests {

    @Test
    @DisplayName("Should have a private constructor")
    void shouldHavePrivateConstructor() throws Exception {
        Constructor<PageableTieBreaker> constructor =
                PageableTieBreaker.class.getDeclaredConstructor();

        assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Nested
    @DisplayName("When using the id tie-breaker")
    class IdTieBreakerTests {

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

    @Nested
    @DisplayName("When using a custom tie-breaker property")
    class CustomTieBreakerTests {

        private static final String PROPERTY = "employeeId";

        @Test
        @DisplayName("Should append the property ascending after the requested sort")
        void shouldAppendPropertyAfterRequestedSort() {
            Pageable result = PageableTieBreaker.withTieBreaker(
                    PageRequest.of(
                            3,
                            15,
                            Sort.by(
                                    Sort.Order.desc("role"),
                                    Sort.Order.asc("active")
                            )
                    ),
                    PROPERTY
            );

            assertEquals(
                    Sort.by(
                            Sort.Order.desc("role"),
                            Sort.Order.asc("active"),
                            Sort.Order.asc(PROPERTY)
                    ),
                    result.getSort()
            );
            assertEquals(3, result.getPageNumber());
            assertEquals(15, result.getPageSize());
        }

        @Test
        @DisplayName("Should sort by the property when the request has no sort")
        void shouldSortByPropertyWhenRequestIsUnsorted() {
            Pageable result = PageableTieBreaker.withTieBreaker(
                    PageRequest.of(0, 10),
                    PROPERTY
            );

            assertEquals(
                    Sort.by(Sort.Order.asc(PROPERTY)),
                    result.getSort()
            );
        }

        @Test
        @DisplayName("Should not add the internal id")
        void shouldNotAddInternalId() {
            Pageable result = PageableTieBreaker.withTieBreaker(
                    PageRequest.of(0, 10, Sort.by("fullName")),
                    PROPERTY
            );

            assertNull(result.getSort().getOrderFor("id"));
        }

        @Test
        @DisplayName("Should keep the pageable when the property is already sorted")
        void shouldKeepPageableWhenPropertyIsAlreadySorted() {
            Pageable pageable = PageRequest.of(
                    0,
                    10,
                    Sort.by(Sort.Order.desc(PROPERTY))
            );

            assertSame(
                    pageable,
                    PageableTieBreaker.withTieBreaker(pageable, PROPERTY)
            );
        }

        @Test
        @DisplayName("Should keep the unpaged pageable unchanged")
        void shouldKeepUnpagedPageable() {
            Pageable pageable = Pageable.unpaged();

            assertSame(
                    pageable,
                    PageableTieBreaker.withTieBreaker(pageable, PROPERTY)
            );
        }
    }
}
