package com.lcsalvess.bankingsystem.validation.daterange;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.validation.daterange.InvalidDateRangeException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Validates date ranges used in API query operations.
 *
 * <p>Ensures dates fall within the supported query period, do not exceed
 * the current date, and follow chronological order.
 */
public final class QueryDateRangeValidator {

  private static final LocalDate MIN_QUERY_DATE =
      LocalDate.of(2020, 1, 1);

  private QueryDateRangeValidator() {
  }

  /**
   * Validates an optional date range against the supported query period.
   *
   * <p>Rejects dates before the minimum supported date, dates in the future,
   * and ranges where the start date is after the end date.
   *
   * @param startDate the optional inclusive start date
   * @param endDate the optional inclusive end date
   * @param clock the application clock used to determine the current date
   * @throws InvalidDateRangeException if either date is outside the supported
   *     period or the start date is after the end date
   * @throws NullPointerException if the clock is null
   */
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
   * Validates an optional date range and enforces a maximum range duration.
   *
   * <p>Applies the standard date range validations and additionally rejects
   * ranges exceeding the specified number of inclusive days.
   *
   * <p>Ranges containing only one date are not subject to the maximum duration.
   *
   * @param startDate the optional inclusive start date
   * @param endDate the optional inclusive end date
   * @param clock the application clock used to determine the current date
   * @param maxRangeDays the maximum permitted number of inclusive days
   * @throws IllegalArgumentException if {@code maxRangeDays} is less than one
   * @throws InvalidDateRangeException if the dates are invalid or the range
   *     exceeds the maximum permitted duration
   * @throws NullPointerException if the clock is null
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
