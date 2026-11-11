package com.lcsalvess.bankingsystem.integration.user;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.util.time.AppTimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("test")
@SpringBootTest
@DisplayName("User timestamps integration")
class UserTimestampsIntegrationTests {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-28T15:30:45.123456789Z");
    private static final Instant CREATED_AT_MICROS =
            Instant.parse("2026-10-28T15:30:45.123456Z");
    private static final Instant UPDATED_AT =
            Instant.parse("2026-10-29T08:00:00.987654321Z");
    private static final Instant UPDATED_AT_MICROS =
            Instant.parse("2026-10-29T08:00:00.987654Z");

    private static final String EMPLOYEE_ID = "EMP-TEST-CLOCK";
    private static final String FULL_NAME = "Clock Test User";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MutableClock mutableClock;

    private Long userId;

    @BeforeEach
    void setUp() {
        mutableClock.freezeAt(CREATED_AT);

        userId = userRepository.saveAndFlush(
                User.createHuman(
                        EMPLOYEE_ID,
                        FULL_NAME,
                        "user-clock-it",
                        "user-clock-it@test.com",
                        "test-password",
                        Role.EMPLOYEE
                )
        ).getId();
    }

    @AfterEach
    void tearDown() {
        mutableClock.useSystemTime();

        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    @DisplayName("Should persist created_at and updated_at from the application clock")
    void shouldPersistTimestampsFromClock() {
        User reloaded = reload();

        assertEquals(CREATED_AT_MICROS, reloaded.getCreatedAt());
        assertEquals(CREATED_AT_MICROS, reloaded.getUpdatedAt());
    }

    @Test
    @DisplayName("Should change only updated_at when the user is updated")
    void shouldChangeOnlyUpdatedAtOnUpdate() {
        User loaded = reload();
        loaded.changePassword("new-test-password");

        mutableClock.freezeAt(UPDATED_AT);
        userRepository.saveAndFlush(loaded);

        User reloaded = reload();

        assertEquals(CREATED_AT_MICROS, reloaded.getCreatedAt());
        assertEquals(UPDATED_AT_MICROS, reloaded.getUpdatedAt());
        assertEquals("new-test-password", reloaded.getPassword());
    }

    private User reload() {
        // The test is not transactional: each repository call uses a new
        // EntityManager, so the user is retrieved directly from the database.
        User user = userRepository.findById(userId).orElse(null);

        assertNotNull(user);
        return user;
    }

    /**
     * Application clock for this test. Uses the actual system time except
     * while a fixed instant is configured, avoiding changes to components
     * that depend on Clock during application initialization.
     */
    static final class MutableClock extends Clock {

        private final Clock systemClock = Clock.system(AppTimeZone.ZONE);
        private volatile Instant frozenInstant;

        void freezeAt(Instant instant) {
            this.frozenInstant = instant;
        }

        void useSystemTime() {
            this.frozenInstant = null;
        }

        @Override
        public ZoneId getZone() {
            return AppTimeZone.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            Instant frozen = frozenInstant;

            return frozen != null
                    ? Clock.fixed(frozen, zone)
                    : Clock.system(zone);
        }

        @Override
        public Instant instant() {
            Instant frozen = frozenInstant;

            return frozen != null ? frozen : systemClock.instant();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockTestConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }
}
