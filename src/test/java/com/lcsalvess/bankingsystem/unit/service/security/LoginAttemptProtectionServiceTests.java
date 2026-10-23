package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.exception.security.LoginRateLimitExceededException;
import com.lcsalvess.bankingsystem.exception.security.LoginRateLimitReason;
import com.lcsalvess.bankingsystem.service.security.LoginAttempt;
import com.lcsalvess.bankingsystem.service.security.LoginAttemptProtectionService;
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

@DisplayName("LoginAttemptProtectionService")
class LoginAttemptProtectionServiceTests {

    private static final Instant INITIAL_INSTANT =
            Instant.parse("2026-01-01T12:00:00Z");

    private static final String REJECTION_METRIC = "login.rate_limit.rejected";

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
    private LoginAttemptProtectionService service;

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

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertEquals(LoginRateLimitReason.CLIENT_IP, exception.getReason());
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

            LoginRateLimitExceededException exception =
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

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertEquals(
                    LoginRateLimitReason.USERNAME_AND_IP,
                    exception.getReason()
            );
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

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertEquals(
                    LoginRateLimitReason.USERNAME_AND_IP,
                    exception.getReason()
            );
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

            LoginRateLimitExceededException exception =
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

            LoginRateLimitExceededException exception =
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

            LoginRateLimitExceededException exception =
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

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertEquals(15L, exception.getRetryAfterSeconds());
        }

        private LoginAttemptProtectionService createServiceWithLock(Duration lock) {
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

            LoginRateLimitExceededException exception =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(
                                    ipNumber(MAX_FAILED_ATTEMPTS_PER_USERNAME + 1),
                                    TEST_USERNAME
                            )
                    );

            assertEquals(LoginRateLimitReason.USERNAME, exception.getReason());
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
            LoginRateLimitExceededException rejected =
                    assertRateLimitedAndGetException(
                            () -> service.beginAttempt(TEST_IP, TEST_USERNAME)
                    );

            assertEquals(LoginRateLimitReason.USERNAME, rejected.getReason());

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
    @DisplayName("Ao registrar métricas")
    class MetricsTests {

        @Test
        @DisplayName("Deve iniciar todos os contadores de rejeição em zero")
        void shouldStartCountersAtZero() {
            for (LoginRateLimitReason reason : LoginRateLimitReason.values()) {
                assertEquals(0.0, rejectionCount(reason));
            }
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

            assertEquals(1.0, rejectionCount(LoginRateLimitReason.CLIENT_IP));
            assertEquals(0.0, rejectionCount(LoginRateLimitReason.USERNAME_AND_IP));
            assertEquals(0.0, rejectionCount(LoginRateLimitReason.USERNAME));
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

            assertEquals(2.0, rejectionCount(LoginRateLimitReason.USERNAME_AND_IP));
            assertEquals(0.0, rejectionCount(LoginRateLimitReason.CLIENT_IP));
            assertEquals(0.0, rejectionCount(LoginRateLimitReason.USERNAME));
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

            assertEquals(1.0, rejectionCount(LoginRateLimitReason.USERNAME));
            assertEquals(0.0, rejectionCount(LoginRateLimitReason.USERNAME_AND_IP));
            assertEquals(0.0, rejectionCount(LoginRateLimitReason.CLIENT_IP));
        }

        private double rejectionCount(LoginRateLimitReason reason) {
            return meterRegistry.get(REJECTION_METRIC)
                    .tag("reason", reason.metricTag())
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
                        } catch (LoginRateLimitExceededException exception) {
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

    private LoginAttemptProtectionService createService(
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

        return new LoginAttemptProtectionService(
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