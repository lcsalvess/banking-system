package com.lcsalvess.bankingsystem.unit.config.security;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("LoginRateLimitProperties")
class LoginRateLimitPropertiesTests {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    @Test
    @DisplayName("Deve aceitar uma configuração válida")
    void shouldAcceptValidConfiguration() {
        assertDoesNotThrow(() -> create(20, 5, 50, ONE_MINUTE, 100));
    }

    @Test
    @DisplayName("Deve aceitar limite por usuário igual ao limite por usuário e IP")
    void shouldAcceptEqualLimits() {
        assertDoesNotThrow(() -> create(20, 5, 5, ONE_MINUTE, 100));
    }

    @Test
    @DisplayName("Deve rejeitar capacidade de IP não positiva")
    void shouldRejectNonPositiveIpCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> create(0, 5, 50, ONE_MINUTE, 100)
        );
    }

    @Test
    @DisplayName("Deve rejeitar limite de tentativas por usuário e IP não positivo")
    void shouldRejectNonPositiveMaxFailedAttempts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> create(20, 0, 50, ONE_MINUTE, 100)
        );
    }

    @Test
    @DisplayName("Deve rejeitar limite de tentativas por usuário não positivo")
    void shouldRejectNonPositiveMaxFailedAttemptsPerUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> create(20, 5, 0, ONE_MINUTE, 100)
        );
    }

    @Test
    @DisplayName("Deve rejeitar limite por usuário menor que o limite por usuário e IP")
    void shouldRejectUsernameLimitLowerThanUsernameAndIpLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> create(20, 5, 4, ONE_MINUTE, 100)
        );
    }

    @Test
    @DisplayName("Deve rejeitar limite de chaves em memória não positivo")
    void shouldRejectNonPositiveMaximumTrackedKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () -> create(20, 5, 50, ONE_MINUTE, 0)
        );
    }

    @Test
    @DisplayName("Deve rejeitar durações nulas, zeradas ou negativas")
    void shouldRejectInvalidDurations() {
        for (Duration invalid : new Duration[]{
                null, Duration.ZERO, Duration.ofSeconds(-1)
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LoginRateLimitProperties(
                            20, invalid, 5, 50, ONE_MINUTE, ONE_MINUTE, 100
                    )
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LoginRateLimitProperties(
                            20, ONE_MINUTE, 5, 50, invalid, ONE_MINUTE, 100
                    )
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LoginRateLimitProperties(
                            20, ONE_MINUTE, 5, 50, ONE_MINUTE, invalid, 100
                    )
            );
        }
    }

    private static LoginRateLimitProperties create(
            int ipCapacity,
            int maxFailedAttempts,
            int maxFailedAttemptsPerUsername,
            Duration duration,
            long maximumTrackedKeys
    ) {
        return new LoginRateLimitProperties(
                ipCapacity,
                duration,
                maxFailedAttempts,
                maxFailedAttemptsPerUsername,
                duration,
                duration,
                maximumTrackedKeys
        );
    }
}