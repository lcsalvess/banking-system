package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.exception.security.LoginRateLimitExceededException;
import com.lcsalvess.bankingsystem.service.security.LoginAttemptProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("LoginAttemptProtectionService")
class LoginAttemptProtectionServiceTests {

    private static final Instant INITIAL_INSTANT =
            Instant.parse("2026-01-01T12:00:00Z");

    private static final String TEST_IP = "192.0.2.10";
    private static final String TEST_USERNAME = "test.user";

    private static final int IP_CAPACITY = 2;
    private static final Duration IP_REFILL_PERIOD = Duration.ofSeconds(10);
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    private static final long MAXIMUM_TRACKED_KEYS = 100;

    private MutableClock clock;
    private LoginAttemptProtectionService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(INITIAL_INSTANT, ZoneOffset.UTC);

        service = createService(
                IP_CAPACITY,
                IP_REFILL_PERIOD,
                MAX_FAILED_ATTEMPTS,
                FAILURE_WINDOW,
                LOCK_DURATION
        );
    }

    @Nested
    @DisplayName("Ao limitar requisições por endereço IP")
    class ClientIpTests {

        @Test
        @DisplayName("Deve permitir requisições dentro da capacidade configurada")
        void shouldAllowRequestsWithinIpCapacity() {
            assertDoesNotThrow(() -> service.checkClientIp(TEST_IP));
            assertDoesNotThrow(() -> service.checkClientIp(TEST_IP));
        }

        @Test
        @DisplayName("Deve rejeitar requisições quando a capacidade do IP se esgota")
        void shouldRejectRequestsWhenIpCapacityIsExhausted() {
            service.checkClientIp(TEST_IP);
            service.checkClientIp(TEST_IP);

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.checkClientIp(TEST_IP)
                    );

            assertEquals(5L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve manter limites independentes para endereços IP diferentes")
        void shouldKeepIndependentBucketsForDifferentIps() {
            service.checkClientIp(TEST_IP);
            service.checkClientIp(TEST_IP);

            assertDoesNotThrow(
                    () -> service.checkClientIp("192.0.2.11")
            );
        }

        @Test
        @DisplayName("Deve tratar endereço nulo ou em branco como IP desconhecido")
        void shouldTreatNullAndBlankIpsAsUnknown() {
            service.checkClientIp(null);
            service.checkClientIp("   ");

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.checkClientIp("unknown")
                    );

            assertEquals(5L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve remover espaços ao redor do endereço IP")
        void shouldTrimIpBeforeApplyingLimit() {
            service.checkClientIp("  " + TEST_IP + "  ");
            service.checkClientIp(TEST_IP);

            assertRateLimited(
                    () -> service.checkClientIp("  " + TEST_IP)
            );
        }

        @Test
        @DisplayName("Deve voltar a permitir requisições quando o bucket recebe tokens")
        void shouldAllowRequestsAfterIpBucketRefills() {
            service.checkClientIp(TEST_IP);
            service.checkClientIp(TEST_IP);

            assertRateLimited(
                    () -> service.checkClientIp(TEST_IP)
            );

            clock.advance(Duration.ofSeconds(5));

            assertDoesNotThrow(
                    () -> service.checkClientIp(TEST_IP)
            );
        }
    }

    @Nested
    @DisplayName("Ao controlar falhas por nome de usuário")
    class UsernameTests {

        @Test
        @DisplayName("Deve permitir um usuário sem falhas registradas")
        void shouldAllowUsernameWithoutPreviousFailures() {
            assertDoesNotThrow(
                    () -> service.checkUsername(TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Não deve bloquear antes de atingir o máximo de falhas")
        void shouldNotBlockBeforeMaximumFailedAttempts() {
            service.recordFailedLogin(TEST_USERNAME);
            service.recordFailedLogin(TEST_USERNAME);

            assertDoesNotThrow(
                    () -> service.checkUsername(TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve bloquear o usuário ao atingir o máximo de falhas")
        void shouldBlockUsernameAfterMaximumFailedAttempts() {
            recordFailedAttempts(TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.checkUsername(TEST_USERNAME)
                    );

            assertEquals(300L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve bloquear após a primeira falha quando o limite configurado é um")
        void shouldBlockOnFirstFailureWhenMaximumFailedAttemptsIsOne() {
            service = createService(
                    IP_CAPACITY,
                    IP_REFILL_PERIOD,
                    1,
                    FAILURE_WINDOW,
                    LOCK_DURATION
            );

            service.recordFailedLogin(TEST_USERNAME);

            assertRateLimited(
                    () -> service.checkUsername(TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve normalizar o nome de usuário sem diferenciar maiúsculas e minúsculas")
        void shouldNormalizeUsernameCase() {
            recordFailedAttempts("TEST.USER", MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.checkUsername("test.user")
            );
        }

        @Test
        @DisplayName("Deve manter o bloqueio ativo sem estender sua duração após novas falhas")
        void shouldNotExtendActiveLockWhenAnotherFailureIsRecorded() {
            recordFailedAttempts(TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            clock.advance(Duration.ofMinutes(1));

            service.recordFailedLogin(TEST_USERNAME);

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.checkUsername(TEST_USERNAME)
                    );

            assertEquals(240L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve reiniciar a contagem quando a janela de falhas expira")
        void shouldResetFailureCountAfterFailureWindowExpires() {
            service.recordFailedLogin(TEST_USERNAME);
            service.recordFailedLogin(TEST_USERNAME);

            clock.advance(FAILURE_WINDOW);

            service.recordFailedLogin(TEST_USERNAME);

            assertDoesNotThrow(
                    () -> service.checkUsername(TEST_USERNAME)
            );

            service.recordFailedLogin(TEST_USERNAME);

            assertDoesNotThrow(
                    () -> service.checkUsername(TEST_USERNAME)
            );

            service.recordFailedLogin(TEST_USERNAME);

            assertRateLimited(
                    () -> service.checkUsername(TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve reiniciar a contagem após o término de um bloqueio")
        void shouldStartNewFailureSequenceAfterLockExpires() {
            recordFailedAttempts(TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.checkUsername(TEST_USERNAME)
            );

            clock.advance(LOCK_DURATION);

            assertDoesNotThrow(
                    () -> service.checkUsername(TEST_USERNAME)
            );

            service.recordFailedLogin(TEST_USERNAME);

            assertDoesNotThrow(
                    () -> service.checkUsername(TEST_USERNAME)
            );

            service.recordFailedLogin(TEST_USERNAME);

            assertDoesNotThrow(
                    () -> service.checkUsername(TEST_USERNAME)
            );

            service.recordFailedLogin(TEST_USERNAME);

            assertRateLimited(
                    () -> service.checkUsername(TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve remover as falhas e o bloqueio após um login bem-sucedido")
        void shouldClearFailureStateAfterSuccessfulLogin() {
            recordFailedAttempts(TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.checkUsername(TEST_USERNAME)
            );

            service.recordSuccessfulLogin("TEST.USER");

            assertDoesNotThrow(
                    () -> service.checkUsername(TEST_USERNAME)
            );

            service.recordFailedLogin(TEST_USERNAME);
            service.recordFailedLogin(TEST_USERNAME);

            assertDoesNotThrow(
                    () -> service.checkUsername(TEST_USERNAME)
            );

            service.recordFailedLogin(TEST_USERNAME);

            assertRateLimited(
                    () -> service.checkUsername(TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve manter o estado de falhas isolado entre usuários diferentes")
        void shouldKeepFailureStateIsolatedBetweenUsernames() {
            recordFailedAttempts(TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.checkUsername(TEST_USERNAME)
            );

            assertDoesNotThrow(
                    () -> service.checkUsername("another.user")
            );
        }

        @Test
        @DisplayName("Deve arredondar para cima o Retry-After quando resta uma fração de segundo")
        void shouldRoundUpRetryAfterWhenLockHasFractionalSecondsRemaining() {
            service = createService(
                    IP_CAPACITY,
                    IP_REFILL_PERIOD,
                    MAX_FAILED_ATTEMPTS,
                    FAILURE_WINDOW,
                    Duration.ofMillis(1500)
            );

            recordFailedAttempts(TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.checkUsername(TEST_USERNAME)
                    );

            assertEquals(2L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve garantir pelo menos um segundo no Retry-After")
        void shouldReturnAtLeastOneSecondForSubsecondLock() {
            service = createService(
                    IP_CAPACITY,
                    IP_REFILL_PERIOD,
                    MAX_FAILED_ATTEMPTS,
                    FAILURE_WINDOW,
                    Duration.ofMillis(500)
            );

            recordFailedAttempts(TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.checkUsername(TEST_USERNAME)
                    );

            assertEquals(1L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve retornar o tempo exato quando Retry-After é um número inteiro de segundos")
        void shouldKeepExactWholeSecondsInRetryAfter() {
            service = createService(
                    IP_CAPACITY,
                    IP_REFILL_PERIOD,
                    MAX_FAILED_ATTEMPTS,
                    FAILURE_WINDOW,
                    Duration.ofSeconds(15)
            );

            recordFailedAttempts(TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.checkUsername(TEST_USERNAME)
                    );

            assertEquals(15L, exception.getRetryAfterSeconds());
        }
    }

    private LoginAttemptProtectionService createService(
            int ipCapacity,
            Duration ipRefillPeriod,
            int maxFailedAttempts,
            Duration failureWindow,
            Duration lockDuration
    ) {
        LoginRateLimitProperties properties = new LoginRateLimitProperties(
                ipCapacity,
                ipRefillPeriod,
                maxFailedAttempts,
                failureWindow,
                lockDuration,
                MAXIMUM_TRACKED_KEYS
        );

        return new LoginAttemptProtectionService(properties, clock);
    }

    private void recordFailedAttempts(String username, int attempts) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            service.recordFailedLogin(username);
        }
    }

    private void assertRateLimited(Executable action) {
        assertThrows(
                LoginRateLimitExceededException.class,
                action
        );
    }

    private LoginRateLimitExceededException assertRateLimitedAndGetException(
            Executable action
    ) {
        return assertThrows(
                LoginRateLimitExceededException.class,
                action
        );
    }

    private static final class MutableClock extends Clock {

        private Instant currentInstant;
        private final ZoneId zone;

        private MutableClock(Instant currentInstant, ZoneId zone) {
            this.currentInstant = currentInstant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }

        @Override
        public long millis() {
            return currentInstant.toEpochMilli();
        }

        private void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }
    }
}