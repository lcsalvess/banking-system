package com.lcsalvess.bankingsystem.validation.daterange;

import com.lcsalvess.bankingsystem.exception.validation.daterange.InvalidDateRangeException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class QueryDateRangeValidator {

    private static final LocalDate MIN_QUERY_DATE =
            LocalDate.of(2020, 1, 1);

    private QueryDateRangeValidator() {
    }

    public static void validate(
            LocalDate startDate,
            LocalDate endDate,
            Clock clock
    ) {
        Objects.requireNonNull(clock, "Clock is required.");

        LocalDate today = LocalDate.now(clock);

        if (isInvalidQueryDate(startDate, today)
                || isInvalidQueryDate(endDate, today)) {
            throw new InvalidDateRangeException(
                    ApiErrorMessages.INVALID_QUERY_DATE
            );
        }

        if (startDate != null
                && endDate != null
                && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException(
                    ApiErrorMessages.INVALID_DATE_RANGE
            );
        }
    }

    /**
     * Same checks as {@link #validate(LocalDate, LocalDate, Clock)}, and
     * additionally rejects a range, with both dates informed, that spans more
     * than {@code maxRangeDays} days (both boundary dates are inclusive).
     *
     * <p>A range with only one of the dates is not capped.
     */
    public static void validate(
            LocalDate startDate,
            LocalDate endDate,
            Clock clock,
            int maxRangeDays
    ) {
        if (maxRangeDays < 1) {
            throw new IllegalArgumentException(
                    "maxRangeDays must be positive."
            );
        }

        validate(startDate, endDate, clock);

        if (startDate != null && endDate != null) {
            long inclusiveDays =
                    ChronoUnit.DAYS.between(startDate, endDate) + 1;

            if (inclusiveDays > maxRangeDays) {
                throw new InvalidDateRangeException(
                        ApiErrorMessages.DATE_RANGE_TOO_WIDE
                                .formatted(maxRangeDays)
                );
            }
        }
    }

    private static boolean isInvalidQueryDate(
            LocalDate date,
            LocalDate today
    ) {
        return date != null
                && (date.isBefore(MIN_QUERY_DATE)
                || date.isAfter(today));
    }
}
