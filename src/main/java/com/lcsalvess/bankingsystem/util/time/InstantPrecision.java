package com.lcsalvess.bankingsystem.util.time;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Normalizes instants to microsecond precision, compatible with PostgreSQL
 * columns defined as {@code TIMESTAMP(6)}.
 *
 * <p>Truncation ensures that in-memory timestamps have the same precision
 * as the values persisted in the database.
 */
public final class InstantPrecision {

    private InstantPrecision() {
    }

    /**
     * Truncates an instant to microsecond precision.
     *
     * @param value the instant to normalize
     * @param nullMessage the exception message if the instant is {@code null}
     * @return the instant truncated to microsecond precision
     * @throws NullPointerException if the instant is {@code null}
     */
    public static Instant truncateToMicros(
            Instant value,
            String nullMessage
    ) {
        return Objects.requireNonNull(value, nullMessage)
                .truncatedTo(ChronoUnit.MICROS);
    }
}
