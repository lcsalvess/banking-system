package com.lcsalvess.bankingsystem.validation.daterange;

import com.lcsalvess.bankingsystem.exception.validation.daterange.InvalidDateRangeException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;

import java.time.Clock;
import java.time.LocalDate;
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

    private static boolean isInvalidQueryDate(
            LocalDate date,
            LocalDate today
    ) {
        return date != null
                && (date.isBefore(MIN_QUERY_DATE)
                || date.isAfter(today));
    }
}