package com.lcsalvess.bankingsystem.unit.validation.daterange;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.validation.daterange.InvalidDateRangeException;
import com.lcsalvess.bankingsystem.validation.daterange.QueryDateRangeValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("QueryDateRangeValidator tests")
class QueryDateRangeValidatorTests {

    private static final ZoneId ZONE =
            ZoneId.of("America/Sao_Paulo");

    private static final LocalDate TODAY =
            LocalDate.of(2026, 10, 26);

    private static final Clock CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ZONE).toInstant(),
            ZONE
    );

    private static final int MAX_DAYS = 366;

    @Nested
    @DisplayName("Maximum range")
    class MaximumRangeTests {

        @Test
        @DisplayName("Should accept a range of exactly the maximum number of days")
        void shouldAcceptRangeOfExactlyMaximumDays() {
            LocalDate start = TODAY.minusDays(MAX_DAYS - 1L);

            assertDoesNotThrow(() ->
                    QueryDateRangeValidator.validate(
                            start, TODAY, CLOCK, MAX_DAYS
                    )
            );
        }

        @Test
        @DisplayName("Should reject a range one day wider than the maximum")
        void shouldRejectRangeOneDayWiderThanMaximum() {
            LocalDate start = TODAY.minusDays(MAX_DAYS);

            InvalidDateRangeException ex = assertThrows(
                    InvalidDateRangeException.class,
                    () -> QueryDateRangeValidator.validate(
                            start, TODAY, CLOCK, MAX_DAYS
                    )
            );

            assertEquals(
                    ApiErrorMessages.DATE_RANGE_TOO_WIDE
                            .formatted(MAX_DAYS),
                    ex.getMessage()
            );
        }

        @Test
        @DisplayName("Should accept a single-day range")
        void shouldAcceptSingleDayRange() {
            assertDoesNotThrow(() ->
                    QueryDateRangeValidator.validate(
                            TODAY, TODAY, CLOCK, MAX_DAYS
                    )
            );
        }

        @Test
        @DisplayName("Should not cap a range with only one date informed")
        void shouldNotCapRangeWithOnlyOneDate() {
            LocalDate oldest = LocalDate.of(2020, 1, 1);

            assertDoesNotThrow(() -> {
                QueryDateRangeValidator.validate(
                        oldest, null, CLOCK, MAX_DAYS
                );
                QueryDateRangeValidator.validate(
                        null, TODAY, CLOCK, MAX_DAYS
                );
                QueryDateRangeValidator.validate(
                        null, null, CLOCK, MAX_DAYS
                );
            });
        }

        @Test
        @DisplayName("Should keep the basic validations when a maximum is given")
        void shouldKeepBasicValidations() {
            InvalidDateRangeException future = assertThrows(
                    InvalidDateRangeException.class,
                    () -> QueryDateRangeValidator.validate(
                            TODAY, TODAY.plusDays(1), CLOCK, MAX_DAYS
                    )
            );
            assertEquals(
                    ApiErrorMessages.INVALID_QUERY_DATE,
                    future.getMessage()
            );

            InvalidDateRangeException inverted = assertThrows(
                    InvalidDateRangeException.class,
                    () -> QueryDateRangeValidator.validate(
                            TODAY, TODAY.minusDays(1), CLOCK, MAX_DAYS
                    )
            );
            assertEquals(
                    ApiErrorMessages.INVALID_DATE_RANGE,
                    inverted.getMessage()
            );
        }

        @Test
        @DisplayName("Should reject a non-positive maximum")
        void shouldRejectNonPositiveMaximum() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> QueryDateRangeValidator.validate(
                            TODAY, TODAY, CLOCK, 0
                    )
            );
        }
    }

    @Nested
    @DisplayName("Without a maximum")
    class WithoutMaximumTests {

        @Test
        @DisplayName("Should accept the widest valid range")
        void shouldAcceptWidestValidRange() {
            assertDoesNotThrow(() ->
                    QueryDateRangeValidator.validate(
                            LocalDate.of(2020, 1, 1), TODAY, CLOCK
                    )
            );
        }
    }
}
