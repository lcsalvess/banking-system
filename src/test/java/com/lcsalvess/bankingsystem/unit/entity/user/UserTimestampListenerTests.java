package com.lcsalvess.bankingsystem.unit.entity.user;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.entity.user.UserTimestampListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("UserTimestampListener")
class UserTimestampListenerTests {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-28T15:30:45.123456789Z");
    private static final Instant CREATED_AT_MICROS =
            Instant.parse("2026-10-28T15:30:45.123456Z");
    private static final Instant UPDATED_AT =
            Instant.parse("2026-10-29T08:00:00.987654321Z");
    private static final Instant UPDATED_AT_MICROS =
            Instant.parse("2026-10-29T08:00:00.987654Z");

    @Test
    @DisplayName("onCreate sets both timestamps from the clock, truncated to micros")
    void onCreateSetsBothTimestamps() {
        User user = newUser();

        new UserTimestampListener(fixedClock(CREATED_AT)).onCreate(user);

        assertEquals(CREATED_AT_MICROS, user.getCreatedAt());
        assertEquals(CREATED_AT_MICROS, user.getUpdatedAt());
    }

    @Test
    @DisplayName("onUpdate changes only updatedAt")
    void onUpdateChangesOnlyUpdatedAt() {
        User user = newUser();
        new UserTimestampListener(fixedClock(CREATED_AT)).onCreate(user);

        new UserTimestampListener(fixedClock(UPDATED_AT)).onUpdate(user);

        assertEquals(CREATED_AT_MICROS, user.getCreatedAt());
        assertEquals(UPDATED_AT_MICROS, user.getUpdatedAt());
    }

    @Test
    @DisplayName("a new user has no timestamps until it is persisted")
    void newUserHasNoTimestamps() {
        User user = newUser();

        assertNull(user.getCreatedAt());
        assertNull(user.getUpdatedAt());
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static User newUser() {
        return new User(
                "employee",
                "employee@example.com",
                "not-a-real-password-hash",
                Role.EMPLOYEE
        );
    }
}
