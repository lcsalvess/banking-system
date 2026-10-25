package com.lcsalvess.bankingsystem.unit.service.security.ratelimit;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.exception.security.RateLimitExceededException;
import com.lcsalvess.bankingsystem.exception.security.RateLimitOperation;
import com.lcsalvess.bankingsystem.exception.security.RateLimitScope;
import com.lcsalvess.bankingsystem.service.security.ratelimit.LoginAttempt;
import com.lcsalvess.bankingsystem.service.security.ratelimit.PasswordChangeAttempt;
import com.lcsalvess.bankingsystem.service.security.ratelimit.RateLimitProtectionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RateLimitProtectionService")
class RateLimitProtectionServiceTests {

    private static final Instant INITIAL_INSTANT =
            Instant.parse("2026-01-01T12:00:00Z");

    private static final String REJECTION_METRIC = "security.rate_limit.rejected";

    private static final String TEST_IP = "192.0.2.10";
    private static final String OTHER_IP = "192.0.2.11";
    private static final String TEST_USERNAME = "test.user";

    // Capacidade alta para que o limite de IP não interfira nos testes de usuário.
    private static final int HIGH_IP_CAPACITY = 1000;
    private static final Duration IP_REFILL_PERIOD = Duration.ofMinutes(1);
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int MAX_FAILED_ATTEMPTS_PER_USERNAME = 6;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    private static final long MAXIMUM_TRACKED_KEYS = 1000;

    private MutableClock clock;
    private SimpleMeterRegistry meterRegistry;
    private RateLimitProtectionService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(INITIAL_INSTANT, ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();

        service = createService(
                HIGH_IP_CAPACITY,
                IP_REFILL_PERIOD,
                MAX_FAILED_ATTEMPTS,
                MAX_FAILED_ATTEMPTS_PER_USERNAME,
                FAILURE_WINDOW,
                LOCK_DURATION
        );
    }

    @Nested
    @DisplayName("Ao limitar requisições por endereço IP")
    class ClientIpTests {

        private static final int IP_CAPACITY = 2;
        private static final Duration REFILL_PERIOD = Duration.ofSeconds(10);

        @BeforeEach
        void useSmallIpBucket() {
            service = createService(
                    IP_CAPACITY,
                    REFILL_PERIOD,
                    100,
                    100,
                    FAILURE_WINDOW,
                    LOCK_DURATION
            );
        }

        @Test
        @DisplayName("Deve permitir requisições dentro da capacidade configurada")
        void shouldAllowRequestsWithinIpCapacity() {
            assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
            assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
        }

        @Test
        @DisplayName("Deve rejeitar requisições quando a capacidade do IP se esgota")
        void shouldRejectRequestsWhenIpCapacityIsExhausted() {
            service.beginAttempt(TEST_IP, TEST_USERNAME);
            service.beginAttempt(TEST_IP, TEST_USERNAME);

            RateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertRejection(exception, RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP);
            assertEquals(5L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve manter limites independentes para endereços IP diferentes")
        void shouldKeepIndependentBucketsForDifferentIps() {
            service.beginAttempt(TEST_IP, TEST_USERNAME);
            service.beginAttempt(TEST_IP, TEST_USERNAME);

            assertDoesNotThrow(() -> service.beginAttempt(OTHER_IP, TEST_USERNAME));
        }

        @Test
        @DisplayName("Deve tratar endereço nulo ou em branco como IP desconhecido")
        void shouldTreatNullAndBlankIpsAsUnknown() {
            service.beginAttempt(null, TEST_USERNAME);
            service.beginAttempt("   ", TEST_USERNAME);

            RateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt("unknown", TEST_USERNAME)
                    );

            assertEquals(5L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve remover espaços ao redor do endereço IP")
        void shouldTrimIpBeforeApplyingLimit() {
            service.beginAttempt("  " + TEST_IP + "  ", TEST_USERNAME);
            service.beginAttempt(TEST_IP, TEST_USERNAME);

            assertRateLimited(
                    () -> service.beginAttempt("  " + TEST_IP, TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve agrupar endereços malformados em um único bucket")
        void shouldGroupMalformedIpsInSingleBucket() {
            service.beginAttempt("not an ip\nwith line break", TEST_USERNAME);
            service.beginAttempt("x".repeat(100), TEST_USERNAME);

            assertRateLimited(
                    () -> service.beginAttempt("also invalid!", TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve voltar a permitir requisições quando o bucket recebe tokens")
        void shouldAllowRequestsAfterIpBucketRefills() {
            service.beginAttempt(TEST_IP, TEST_USERNAME);
            service.beginAttempt(TEST_IP, TEST_USERNAME);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );

            clock.advance(Duration.ofSeconds(5));

            assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
        }

        @Test
        @DisplayName("Não deve contabilizar para o usuário uma requisição rejeitada pelo IP")
        void shouldNotCountAttemptRejectedByClientIp() {
            service = createService(
                    1,
                    Duration.ofSeconds(10),
                    MAX_FAILED_ATTEMPTS,
                    MAX_FAILED_ATTEMPTS_PER_USERNAME,
                    FAILURE_WINDOW,
                    LOCK_DURATION
            );

            service.beginAttempt(TEST_IP, TEST_USERNAME); // contagem 1

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );

            clock.advance(Duration.ofSeconds(10));
            service.beginAttempt(TEST_IP, TEST_USERNAME); // contagem 2

            clock.advance(Duration.ofSeconds(10));
            service.beginAttempt(TEST_IP, TEST_USERNAME); // contagem 3, inicia o bloqueio

            clock.advance(Duration.ofSeconds(10));

            RateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertRejection(exception, RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP);
        }
    }

    @Nested
    @DisplayName("Ao limitar tentativas por usuário e IP")
    class UsernameAndIpTests {

        @Test
        @DisplayName("Não deve bloquear antes de atingir o limite")
        void shouldNotBlockBeforeMaximumAttempts() {
            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS - 1);

            assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
        }

        @Test
        @DisplayName("Deve bloquear a tentativa seguinte ao atingir o limite")
        void shouldBlockAfterMaximumAttempts() {
            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            RateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertRejection(exception, RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP);
            assertEquals(300L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve bloquear após a primeira tentativa quando o limite configurado é um")
        void shouldBlockAfterFirstAttemptWhenMaximumIsOne() {
            service = createService(
                    HIGH_IP_CAPACITY,
                    IP_REFILL_PERIOD,
                    1,
                    1,
                    FAILURE_WINDOW,
                    LOCK_DURATION
            );

            service.beginAttempt(TEST_IP, TEST_USERNAME);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve normalizar o nome de usuário sem diferenciar maiúsculas e minúsculas")
        void shouldNormalizeUsernameCase() {
            beginAttempts(TEST_IP, "TEST.USER", MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, "test.user")
            );
        }

        @Test
        @DisplayName("Não deve bloquear o mesmo usuário em outro endereço IP")
        void shouldNotBlockSameUsernameFromAnotherIp() {
            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );

            assertDoesNotThrow(() -> service.beginAttempt(OTHER_IP, TEST_USERNAME));
        }

        @Test
        @DisplayName("Não deve bloquear outro usuário no mesmo endereço IP")
        void shouldNotBlockAnotherUsernameFromSameIp() {
            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, "another.user"));
        }

        @Test
        @DisplayName("Deve manter o bloqueio ativo sem estender sua duração")
        void shouldNotExtendActiveLockWhenAnotherAttemptIsRejected() {
            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            clock.advance(Duration.ofMinutes(1));

            RateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertEquals(240L, exception.getRetryAfterSeconds());

            // O bloqueio original termina 5 minutos após o início.
            clock.advance(Duration.ofMinutes(4));

            assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
        }

        @Test
        @DisplayName("Deve reiniciar a contagem quando a janela de falhas expira")
        void shouldResetCountAfterFailureWindowExpires() {
            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS - 1);

            clock.advance(FAILURE_WINDOW);

            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve reiniciar a contagem após o término de um bloqueio")
        void shouldStartNewSequenceAfterLockExpires() {
            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );

            clock.advance(LOCK_DURATION);

            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve zerar a contagem e o bloqueio após um login bem-sucedido")
        void shouldClearStateAfterSuccessfulLogin() {
            LoginAttempt lastAttempt = null;

            for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
                lastAttempt = service.beginAttempt(TEST_IP, TEST_USERNAME);
            }

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );

            service.recordSuccessfulLogin(lastAttempt);

            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve desfazer a contagem de uma tentativa cancelada")
        void shouldReleaseCancelledAttempt() {
            service.beginAttempt(TEST_IP, TEST_USERNAME);
            LoginAttempt cancelled = service.beginAttempt(TEST_IP, TEST_USERNAME);

            service.cancelAttempt(cancelled);

            // Restava uma contagem; duas novas tentativas atingem o limite.
            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS - 1);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve encerrar o bloqueio iniciado pela tentativa cancelada")
        void shouldLiftLockStartedByCancelledAttempt() {
            LoginAttempt lastAttempt = null;

            for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
                lastAttempt = service.beginAttempt(TEST_IP, TEST_USERNAME);
            }

            service.cancelAttempt(lastAttempt);

            assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Não deve alterar uma sequência mais nova ao cancelar uma tentativa antiga")
        void shouldNotAffectNewerSequenceWhenCancellingOldAttempt() {
            LoginAttempt oldAttempt = service.beginAttempt(TEST_IP, TEST_USERNAME);

            clock.advance(FAILURE_WINDOW);

            service.beginAttempt(TEST_IP, TEST_USERNAME); // nova sequência, contagem 1

            service.cancelAttempt(oldAttempt);

            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS - 1);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );
        }

        @Test
        @DisplayName("Deve arredondar para cima o Retry-After quando resta uma fração de segundo")
        void shouldRoundUpRetryAfterWhenLockHasFractionalSeconds() {
            service = createServiceWithLock(Duration.ofMillis(1500));

            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            RateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertEquals(2L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve garantir pelo menos um segundo no Retry-After")
        void shouldReturnAtLeastOneSecondForSubsecondLock() {
            service = createServiceWithLock(Duration.ofMillis(500));

            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            RateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertEquals(1L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve retornar o tempo exato quando Retry-After é um número inteiro de segundos")
        void shouldKeepExactWholeSecondsInRetryAfter() {
            service = createServiceWithLock(Duration.ofSeconds(15));

            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            RateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertEquals(15L, exception.getRetryAfterSeconds());
        }

        private RateLimitProtectionService createServiceWithLock(Duration lock) {
            return createService(
                    HIGH_IP_CAPACITY,
                    IP_REFILL_PERIOD,
                    MAX_FAILED_ATTEMPTS,
                    MAX_FAILED_ATTEMPTS_PER_USERNAME,
                    FAILURE_WINDOW,
                    lock
            );
        }
    }

    @Nested
    @DisplayName("Ao limitar tentativas por usuário em todos os IPs")
    class UsernameTests {

        @Test
        @DisplayName("Deve bloquear o usuário ao atingir o limite somando IPs diferentes")
        void shouldBlockUsernameAcrossDifferentIps() {
            beginAttemptsFromDistinctIps(1, MAX_FAILED_ATTEMPTS_PER_USERNAME);

            RateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(
                                    ipNumber(MAX_FAILED_ATTEMPTS_PER_USERNAME + 1),
                                    TEST_USERNAME
                            )
                    );

            assertRejection(exception, RateLimitOperation.LOGIN, RateLimitScope.USERNAME);
            assertEquals(300L, exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve normalizar o nome de usuário no limite global")
        void shouldNormalizeUsernameInGlobalLimit() {
            for (int i = 1; i <= MAX_FAILED_ATTEMPTS_PER_USERNAME; i++) {
                service.beginAttempt(ipNumber(i), "TEST.USER");
            }

            assertRateLimited(
                    () -> service.beginAttempt(ipNumber(100), "test.user")
            );
        }

        @Test
        @DisplayName("Não deve afetar outro usuário")
        void shouldNotAffectAnotherUsername() {
            beginAttemptsFromDistinctIps(1, MAX_FAILED_ATTEMPTS_PER_USERNAME);

            assertDoesNotThrow(
                    () -> service.beginAttempt(ipNumber(100), "another.user")
            );
        }

        @Test
        @DisplayName("Não deve contabilizar na combinação usuário e IP uma tentativa rejeitada pelo usuário")
        void shouldReleaseUsernameAndIpWhenRejectedByUsername() {
            // O IP de teste acumula duas tentativas; os demais IPs completam o limite do usuário.
            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS - 1);
            beginAttemptsFromDistinctIps(
                    1,
                    MAX_FAILED_ATTEMPTS_PER_USERNAME - (MAX_FAILED_ATTEMPTS - 1)
            );

            clock.advance(Duration.ofMinutes(1));

            // Rejeitada pelo limite do usuário: não pode somar na combinação usuário e IP.
            RateLimitExceededException rejected =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertRejection(rejected, RateLimitOperation.LOGIN, RateLimitScope.USERNAME);

            // O bloqueio do usuário termina; a combinação deve estar livre.
            clock.advance(Duration.ofMinutes(4));

            assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));
        }

        @Test
        @DisplayName("Deve zerar o contador do usuário após um login bem-sucedido")
        void shouldClearUsernameCounterAfterSuccessfulLogin() {
            LoginAttempt lastAttempt = null;

            for (int i = 1; i < MAX_FAILED_ATTEMPTS_PER_USERNAME; i++) {
                lastAttempt = service.beginAttempt(ipNumber(i), TEST_USERNAME);
            }

            service.recordSuccessfulLogin(lastAttempt);

            beginAttemptsFromDistinctIps(100, MAX_FAILED_ATTEMPTS_PER_USERNAME);

            assertRateLimited(
                    () -> service.beginAttempt(ipNumber(200), TEST_USERNAME)
            );
        }

        private void beginAttemptsFromDistinctIps(int firstIpNumber, int count) {
            for (int i = 0; i < count; i++) {
                service.beginAttempt(ipNumber(firstIpNumber + i), TEST_USERNAME);
            }
        }
    }

    @Nested
    @DisplayName("Ao limpar falhas de um usuário")
    class ClearFailuresTests {

        @Test
        @DisplayName("Deve remover o bloqueio por usuário e IP sem afetar outros usuários")
        void shouldClearUsernameAndIpLockOfOnlyThatUser() {
            beginAttempts(TEST_IP, "user.a", MAX_FAILED_ATTEMPTS);
            beginAttempts(TEST_IP, "user.b", MAX_FAILED_ATTEMPTS);

            service.clearFailures("USER.A");

            assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, "user.a"));
            assertRateLimited(() -> service.beginAttempt(TEST_IP, "user.b"));
        }

        @Test
        @DisplayName("Deve remover o bloqueio do usuário em todos os IPs")
        void shouldClearUsernameLock() {
            for (int i = 1; i <= MAX_FAILED_ATTEMPTS_PER_USERNAME; i++) {
                service.beginAttempt(ipNumber(i), TEST_USERNAME);
            }

            assertRateLimited(
                    () -> service.beginAttempt(ipNumber(100), TEST_USERNAME)
            );

            service.clearFailures(TEST_USERNAME);

            assertDoesNotThrow(
                    () -> service.beginAttempt(ipNumber(100), TEST_USERNAME)
            );
        }
    }

    @Nested
    @DisplayName("Ao limitar tentativas de troca de senha")
    class PasswordChangeTests {

        private static final long USER_ID = 1L;
        private static final long OTHER_USER_ID = 2L;

        private void beginPasswordChangeAttempts(long userId, int attempts) {
            for (int attempt = 0; attempt < attempts; attempt++) {
                service.beginPasswordChangeAttempt(userId);
            }
        }

        @Test
        @DisplayName("Deve rejeitar a tentativa seguinte ao limite, com o motivo PASSWORD_CHANGE")
        void shouldRejectAttemptAfterLimit() {
            beginPasswordChangeAttempts(USER_ID, MAX_FAILED_ATTEMPTS);

            RateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginPasswordChangeAttempt(USER_ID)
                    );

            assertRejection(exception, RateLimitOperation.PASSWORD_CHANGE, RateLimitScope.USER_ID);
            assertEquals(LOCK_DURATION.toSeconds(), exception.getRetryAfterSeconds());
        }

        @Test
        @DisplayName("Deve manter contadores independentes por usuário")
        void shouldKeepIndependentCountersPerUser() {
            beginPasswordChangeAttempts(USER_ID, MAX_FAILED_ATTEMPTS);

            assertRateLimited(() -> service.beginPasswordChangeAttempt(USER_ID));
            assertDoesNotThrow(() -> service.beginPasswordChangeAttempt(OTHER_USER_ID));
        }

        @Test
        @DisplayName("Não deve bloquear o login do usuário nem ser bloqueada por ele")
        void shouldBeIndependentFromLoginLimits() {
            beginPasswordChangeAttempts(USER_ID, MAX_FAILED_ATTEMPTS);
            assertRateLimited(() -> service.beginPasswordChangeAttempt(USER_ID));

            assertDoesNotThrow(() -> service.beginAttempt(TEST_IP, TEST_USERNAME));

            beginAttempts(OTHER_IP, "other.user", MAX_FAILED_ATTEMPTS);
            assertRateLimited(() -> service.beginAttempt(OTHER_IP, "other.user"));

            assertDoesNotThrow(() -> service.beginPasswordChangeAttempt(OTHER_USER_ID));
        }

        @Test
        @DisplayName("Deve zerar o contador após a confirmação da senha atual")
        void shouldResetCounterAfterSuccessfulPasswordChange() {
            service.beginPasswordChangeAttempt(USER_ID);
            service.beginPasswordChangeAttempt(USER_ID);
            PasswordChangeAttempt last = service.beginPasswordChangeAttempt(USER_ID);

            service.recordSuccessfulCurrentPasswordVerification(last);

            beginPasswordChangeAttempts(USER_ID, MAX_FAILED_ATTEMPTS);
            assertRateLimited(() -> service.beginPasswordChangeAttempt(USER_ID));
        }

        @Test
        @DisplayName("Deve desfazer a contagem de uma tentativa cancelada")
        void shouldReleaseCancelledAttempt() {
            service.beginPasswordChangeAttempt(USER_ID);
            service.beginPasswordChangeAttempt(USER_ID);
            PasswordChangeAttempt third = service.beginPasswordChangeAttempt(USER_ID);

            service.cancelPasswordChangeAttempt(third);

            assertDoesNotThrow(() -> service.beginPasswordChangeAttempt(USER_ID));
            assertRateLimited(() -> service.beginPasswordChangeAttempt(USER_ID));
        }

        @Test
        @DisplayName("Deve liberar novas tentativas quando o bloqueio termina")
        void shouldAllowAttemptsAfterLockDuration() {
            beginPasswordChangeAttempts(USER_ID, MAX_FAILED_ATTEMPTS);
            assertRateLimited(() -> service.beginPasswordChangeAttempt(USER_ID));

            clock.advance(LOCK_DURATION);

            assertDoesNotThrow(() -> service.beginPasswordChangeAttempt(USER_ID));
        }
    }

    @Nested
    @DisplayName("Ao registrar métricas")
    class MetricsTests {

        @Test
        @DisplayName("Deve iniciar todos os contadores de rejeição em zero")
        void shouldStartCountersAtZero() {
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME));
            assertEquals(0.0, rejectionCount(RateLimitOperation.PASSWORD_CHANGE, RateLimitScope.USER_ID));
        }

        @Test
        @DisplayName("Deve contar rejeições por limite de IP")
        void shouldCountClientIpRejections() {
            service = createService(
                    1,
                    IP_REFILL_PERIOD,
                    MAX_FAILED_ATTEMPTS,
                    MAX_FAILED_ATTEMPTS_PER_USERNAME,
                    FAILURE_WINDOW,
                    LOCK_DURATION
            );

            service.beginAttempt(TEST_IP, TEST_USERNAME);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );

            assertEquals(1.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME));
        }

        @Test
        @DisplayName("Deve contar rejeições por usuário e IP")
        void shouldCountUsernameAndIpRejections() {
            beginAttempts(TEST_IP, TEST_USERNAME, MAX_FAILED_ATTEMPTS);

            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );
            assertRateLimited(
                    () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
            );

            assertEquals(2.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME));
        }

        @Test
        @DisplayName("Deve contar rejeições por usuário")
        void shouldCountUsernameRejections() {
            for (int i = 1; i <= MAX_FAILED_ATTEMPTS_PER_USERNAME; i++) {
                service.beginAttempt(ipNumber(i), TEST_USERNAME);
            }

            assertRateLimited(
                    () -> service.beginAttempt(ipNumber(100), TEST_USERNAME)
            );

            assertEquals(1.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP));
        }

        @Test
        @DisplayName("Deve contar rejeições de troca de senha")
        void shouldCountPasswordChangeRejections() {
            for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
                service.beginPasswordChangeAttempt(1L);
            }

            assertRateLimited(() -> service.beginPasswordChangeAttempt(1L));

            assertEquals(1.0, rejectionCount(RateLimitOperation.PASSWORD_CHANGE, RateLimitScope.USER_ID));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.CLIENT_IP));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME_AND_IP));
            assertEquals(0.0, rejectionCount(RateLimitOperation.LOGIN, RateLimitScope.USERNAME));
        }

        private double rejectionCount(
                RateLimitOperation operation,
                RateLimitScope scope
        ) {
            return meterRegistry.get(REJECTION_METRIC)
                    .tag("operation", operation.metricTag())
                    .tag("scope", scope.metricTag())
                    .counter()
                    .count();
        }
    }

    @Nested
    @DisplayName("Ao receber requisições simultâneas")
    class ConcurrencyTests {

        private static final int THREADS = 50;

        @Test
        @DisplayName("Deve admitir no máximo o limite por usuário e IP, mesmo em paralelo")
        void shouldAdmitOnlyMaximumAttemptsForSameUsernameAndIp() throws Exception {
            service = createService(
                    HIGH_IP_CAPACITY,
                    IP_REFILL_PERIOD,
                    5,
                    100,
                    FAILURE_WINDOW,
                    LOCK_DURATION
            );

            int admitted = countAdmittedConcurrently(index -> TEST_IP);

            assertEquals(5, admitted);
        }

        @Test
        @DisplayName("Deve admitir no máximo o limite por usuário, mesmo em paralelo com IPs diferentes")
        void shouldAdmitOnlyMaximumAttemptsForSameUsername() throws Exception {
            service = createService(
                    HIGH_IP_CAPACITY,
                    IP_REFILL_PERIOD,
                    3,
                    10,
                    FAILURE_WINDOW,
                    LOCK_DURATION
            );

            int admitted = countAdmittedConcurrently(
                    ConcurrencyTests::ipNumberForThread
            );

            assertEquals(10, admitted);
        }

        private static String ipNumberForThread(int index) {
            return "198.51.100." + (index + 1);
        }

        private int countAdmittedConcurrently(IntFunction<String> ipForThread)
                throws Exception {

            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger admitted = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            List<Future<Void>> futures = new ArrayList<>();

            try {
                for (int i = 0; i < THREADS; i++) {
                    String ip = ipForThread.apply(i);

                    Callable<Void> task = () -> {
                        ready.countDown();
                        start.await();

                        try {
                            service.beginAttempt(ip, TEST_USERNAME);
                            admitted.incrementAndGet();
                        } catch (RateLimitExceededException exception) {
                            rejected.incrementAndGet();
                        }

                        return null;
                    };

                    futures.add(executor.submit(task));
                }

                ready.await();
                start.countDown();

                for (Future<Void> future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }

            assertEquals(THREADS, admitted.get() + rejected.get());

            return admitted.get();
        }
    }

    private RateLimitProtectionService createService(
            int ipCapacity,
            Duration ipRefillPeriod,
            int maxFailedAttempts,
            int maxFailedAttemptsPerUsername,
            Duration failureWindow,
            Duration lockDuration
    ) {
        LoginRateLimitProperties properties = new LoginRateLimitProperties(
                ipCapacity,
                ipRefillPeriod,
                maxFailedAttempts,
                maxFailedAttemptsPerUsername,
                failureWindow,
                lockDuration,
                MAXIMUM_TRACKED_KEYS
        );

        return new RateLimitProtectionService(
                properties,
                clock,
                meterRegistry
        );
    }

    private void beginAttempts(String ip, String username, int attempts) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            service.beginAttempt(ip, username);
        }
    }

    private static String ipNumber(int number) {
        return "203.0.113." + number;
    }

    private void assertRateLimited(Executable action) {
        assertThrows(
                RateLimitExceededException.class,
                action
        );
    }

    private static void assertRejection(
            RateLimitExceededException exception,
            RateLimitOperation expectedOperation,
            RateLimitScope expectedScope
    ) {
        assertEquals(expectedOperation, exception.getOperation());
        assertEquals(expectedScope, exception.getScope());
    }

    private RateLimitExceededException assertRateLimitedAndGetException(
            Executable action
    ) {
        return assertThrows(
                RateLimitExceededException.class,
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