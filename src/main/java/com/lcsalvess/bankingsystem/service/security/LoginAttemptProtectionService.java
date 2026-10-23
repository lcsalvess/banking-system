package com.lcsalvess.bankingsystem.service.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.exception.security.LoginRateLimitExceededException;
import com.lcsalvess.bankingsystem.exception.security.LoginRateLimitReason;
import com.lcsalvess.bankingsystem.util.normalization.UsernameNormalizer;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.caffeine.Bucket4jCaffeine;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Limita as tentativas de login com três controles independentes:
 *
 * <ol>
 *     <li><b>IP:</b> bucket de requisições por endereço IP (Bucket4j).</li>
 *     <li><b>Usuário + IP:</b> número máximo de tentativas por combinação de
 *     nome de usuário e IP dentro da janela. Como a chave inclui o IP, quem
 *     erra a senha de um usuário bloqueia apenas o próprio IP, e não o
 *     usuário legítimo.</li>
 *     <li><b>Usuário:</b> limite maior, somando todos os IPs, que contém
 *     tentativas distribuídas contra uma mesma conta.</li>
 * </ol>
 *
 * <p>A tentativa é contabilizada <b>antes</b> da verificação da senha, de forma
 * atômica ({@code compute}). Assim, requisições paralelas não conseguem
 * ultrapassar o limite enquanto o BCrypt ainda está em execução. Um login
 * bem-sucedido zera os contadores, e uma falha de infraestrutura desfaz a
 * contagem ({@link #cancelAttempt(LoginAttempt)}).
 *
 * <p>O estado fica em memória (Caffeine), não é compartilhado entre instâncias
 * e é perdido ao reiniciar. Com o cache cheio, o Caffeine pode descartar
 * entradas, o que encerra um bloqueio antes do prazo.
 */
@Service
public class LoginAttemptProtectionService {

    private static final Logger log =
            LoggerFactory.getLogger(LoginAttemptProtectionService.class);

    private static final String REJECTION_METRIC = "login.rate_limit.rejected";

    private static final Duration BUCKET_RETENTION_AFTER_REFILL =
            Duration.ofSeconds(30);

    private static final String UNKNOWN_IP = "unknown";
    private static final String INVALID_IP = "invalid";

    // Texto aceitável para um endereço IPv4/IPv6. Qualquer outro valor é
    // agrupado em um único bucket, o que evita chaves enormes e injeção em logs.
    private static final Pattern IP_TEXT = Pattern.compile("[0-9A-Za-z:.%-]{1,64}");

    private static final String USERNAME_AND_IP_PREFIX = "username-ip:";
    private static final String USERNAME_PREFIX = "username:";

    private final LoginRateLimitProperties properties;
    private final Clock clock;

    private final ProxyManager<String> ipBucketManager;

    private final Cache<String, AttemptState> attemptStates;

    private final Map<LoginRateLimitReason, Counter> rejectionCounters =
            new EnumMap<>(LoginRateLimitReason.class);

    public LoginAttemptProtectionService(
            LoginRateLimitProperties properties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.clock = clock;

        this.ipBucketManager = Bucket4jCaffeine
                .<String>builderFor(
                        Caffeine.newBuilder()
                                .maximumSize(properties.maximumTrackedKeys())
                )
                .clientClock(toTimeMeter(clock))
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(
                                        BUCKET_RETENTION_AFTER_REFILL
                                )
                )
                .build();

        this.attemptStates = Caffeine.newBuilder()
                .maximumSize(properties.maximumTrackedKeys())
                .expireAfterWrite(
                        properties.failureWindow()
                                .plus(properties.lockDuration())
                )
                .build();

        for (LoginRateLimitReason reason : LoginRateLimitReason.values()) {
            rejectionCounters.put(
                    reason,
                    Counter.builder(REJECTION_METRIC)
                            .description("Login requests rejected by the rate limiter")
                            .tag("reason", reason.metricTag())
                            .register(meterRegistry)
            );
        }
    }

    /**
     * Admite uma tentativa de login ou lança
     * {@link LoginRateLimitExceededException}.
     *
     * <p>Consome um token do IP e contabiliza a tentativa para a combinação
     * usuário + IP e para o usuário. Quem recebe a tentativa deve chamar
     * {@link #recordSuccessfulLogin(LoginAttempt)} se as credenciais forem
     * válidas, ou {@link #cancelAttempt(LoginAttempt)} se a verificação não
     * puder ser concluída (falha de infraestrutura).
     */
    public LoginAttempt beginAttempt(String clientIp, String username) {
        String ip = normalizeIp(clientIp);

        consumeIpToken(ip);

        String normalizedUsername = normalizeUsername(username);
        String usernameAndIpKey = usernameAndIpKey(normalizedUsername, ip);
        String usernameKey = usernameKey(normalizedUsername);
        Instant now = clock.instant();

        Instant usernameAndIpWindowStartedAt = reserve(
                usernameAndIpKey,
                properties.maxFailedAttempts(),
                LoginRateLimitReason.USERNAME_AND_IP,
                ip,
                now
        );

        Instant usernameWindowStartedAt;

        try {
            usernameWindowStartedAt = reserve(
                    usernameKey,
                    properties.maxFailedAttemptsPerUsername(),
                    LoginRateLimitReason.USERNAME,
                    ip,
                    now
            );
        } catch (LoginRateLimitExceededException exception) {
            // A tentativa não foi admitida: não deve pesar na combinação usuário + IP.
            release(usernameAndIpKey, usernameAndIpWindowStartedAt);
            throw exception;
        }

        return new LoginAttempt(
                usernameAndIpKey,
                usernameAndIpWindowStartedAt,
                usernameKey,
                usernameWindowStartedAt
        );
    }

    /**
     * Zera os contadores do usuário e da combinação usuário + IP após um
     * login com credenciais válidas.
     */
    public void recordSuccessfulLogin(LoginAttempt attempt) {
        attemptStates.invalidate(attempt.usernameAndIpKey());
        attemptStates.invalidate(attempt.usernameKey());
    }

    /**
     * Desfaz a contagem de uma tentativa que não pôde ser avaliada, como uma
     * falha de infraestrutura. Não altera sequências iniciadas depois dela.
     */
    public void cancelAttempt(LoginAttempt attempt) {
        release(attempt.usernameAndIpKey(), attempt.usernameAndIpWindowStartedAt());
        release(attempt.usernameKey(), attempt.usernameWindowStartedAt());
    }

    /**
     * Remove contadores e bloqueios de um usuário, em qualquer IP. Percorre as
     * chaves em memória, por isso é indicado para operações pontuais
     * (desbloqueio administrativo, testes), não para o fluxo de login.
     */
    public void clearFailures(String username) {
        String normalizedUsername = normalizeUsername(username);
        String prefix = usernameAndIpKey(normalizedUsername, "");

        attemptStates.invalidate(usernameKey(normalizedUsername));
        attemptStates.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void consumeIpToken(String ip) {
        Bucket bucket = ipBucketManager.getProxy(
                "ip:" + ip,
                this::createIpBucketConfiguration
        );

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            throw rejection(
                    LoginRateLimitReason.CLIENT_IP,
                    Duration.ofNanos(probe.getNanosToWaitForRefill()),
                    ip
            );
        }
    }

    /**
     * Contabiliza uma tentativa na chave informada e devolve o início da
     * janela em que ela foi contada. Lança a exceção se a chave estiver
     * bloqueada. Quando a contagem atinge o limite, o bloqueio começa nesse
     * momento: a tentativa que atingiu o limite ainda é avaliada, mas a
     * seguinte já é rejeitada.
     */
    private Instant reserve(
            String key,
            int limit,
            LoginRateLimitReason reason,
            String ip,
            Instant now
    ) {
        AtomicReference<Instant> windowStartedAt = new AtomicReference<>();

        attemptStates.asMap().compute(key, (ignored, previous) -> {
            AttemptState current = activeState(previous, now);

            // Bloqueio ativo: rejeita sem alterar nem prorrogar o estado.
            if (current != null && current.blockedUntil() != null) {
                throw rejection(
                        reason,
                        Duration.between(now, current.blockedUntil()),
                        ip
                );
            }

            Instant started = current == null ? now : current.windowStartedAt();
            int attempts = current == null ? 1 : current.attempts() + 1;

            Instant blockedUntil = attempts >= limit
                    ? now.plus(properties.lockDuration())
                    : null;

            windowStartedAt.set(started);

            return new AttemptState(attempts, started, blockedUntil);
        });

        return windowStartedAt.get();
    }

    private void release(String key, Instant windowStartedAt) {
        attemptStates.asMap().computeIfPresent(key, (ignored, state) -> {
            // Uma sequência mais nova não pertence a esta tentativa.
            if (!state.windowStartedAt().equals(windowStartedAt)) {
                return state;
            }

            int attempts = state.attempts() - 1;

            if (attempts < 1) {
                return null;
            }

            return new AttemptState(attempts, state.windowStartedAt(), null);
        });
    }

    /**
     * Devolve o estado ainda válido ou {@code null} quando ele não vale mais:
     * bloqueio expirado ou janela de falhas encerrada iniciam uma nova sequência.
     */
    private AttemptState activeState(AttemptState previous, Instant now) {
        if (previous == null) {
            return null;
        }

        if (previous.blockedUntil() != null) {
            return now.isBefore(previous.blockedUntil()) ? previous : null;
        }

        boolean windowExpired = !now.isBefore(
                previous.windowStartedAt().plus(properties.failureWindow())
        );

        return windowExpired ? null : previous;
    }

    private LoginRateLimitExceededException rejection(
            LoginRateLimitReason reason,
            Duration wait,
            String ip
    ) {
        rejectionCounters.get(reason).increment();

        log.warn(
                "Login rejected: rate limit exceeded. reason={}, clientIp={}",
                reason,
                ip
        );

        return new LoginRateLimitExceededException(retryAfterSeconds(wait), reason);
    }

    private static TimeMeter toTimeMeter(Clock clock) {
        return new TimeMeter() {

            @Override
            public long currentTimeNanos() {
                return TimeUnit.MILLISECONDS.toNanos(clock.millis());
            }

            @Override
            public boolean isWallClockBased() {
                return true;
            }
        };
    }

    private BucketConfiguration createIpBucketConfiguration() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(properties.ipCapacity())
                .refillGreedy(
                        properties.ipCapacity(),
                        properties.ipRefillPeriod()
                )
                .build();

        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }

    private static String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return UNKNOWN_IP;
        }

        String ip = clientIp.trim();

        return IP_TEXT.matcher(ip).matches() ? ip : INVALID_IP;
    }

    private static String normalizeUsername(String username) {
        String normalized = UsernameNormalizer.normalize(username);

        return normalized == null ? "" : normalized;
    }

    private static String usernameAndIpKey(String normalizedUsername, String ip) {
        return USERNAME_AND_IP_PREFIX + normalizedUsername + "|" + ip;
    }

    private static String usernameKey(String normalizedUsername) {
        return USERNAME_PREFIX + normalizedUsername;
    }

    private long retryAfterSeconds(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return 1L;
        }

        long seconds = duration.getSeconds();

        if (duration.getNano() > 0) {
            seconds++;
        }

        return Math.max(1L, seconds);
    }

    private record AttemptState(
            int attempts,
            Instant windowStartedAt,
            Instant blockedUntil
    ) {
    }
}