package com.lcsalvess.bankingsystem.unit.util.time;

import com.lcsalvess.bankingsystem.util.time.InstantPrecision;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstantPrecisionTest {

    @Test
    void shouldTruncateInstantToMicrosecondPrecision() {
        Instant value = Instant.parse(
                "2026-10-30T12:34:56.123456789Z"
        );

        Instant result = InstantPrecision.truncateToMicros(
                value,
                "Timestamp is required."
        );

        assertEquals(
                Instant.parse("2026-10-30T12:34:56.123456Z"),
                result
        );
    }

    @Test
    void shouldPreserveInstantAlreadyAtMicrosecondPrecision() {
        Instant value = Instant.parse(
                "2026-10-30T12:34:56.123456Z"
        );

        Instant result = InstantPrecision.truncateToMicros(
                value,
                "Timestamp is required."
        );

        assertEquals(value, result);
    }

    @Test
    void shouldThrowExceptionWhenInstantIsNull() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> InstantPrecision.truncateToMicros(
                        null,
                        "Timestamp is required."
                )
        );

        assertEquals(
                "Timestamp is required.",
                exception.getMessage()
        );
    }
}
