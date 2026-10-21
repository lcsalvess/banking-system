package com.lcsalvess.bankingsystem.service.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.exception.security.LoginRateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.caffeine.Bucket4jCaffeine;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class LoginAttemptProtectionService {

    private static final Duration BUCKET_RETENTION_AFTER_REFILL =
            Duration.ofSeconds(30);

    private final LoginRateLimitProperties properties;
    private final Clock clock;

    private final ProxyManager<String> ipBucketManager;

    private final Cache<String, FailedLoginState> failedLogins;

    public LoginAttemptProtectionService(
            LoginRateLimitProperties properties,
            Clock clock
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

        this.failedLogins = Caffeine.newBuilder()
                .maximumSize(properties.maximumTrackedKeys())
                .expireAfterWrite(
                        properties.failureWindow()
                                .plus(properties.lockDuration())
                )
                .build();
    }

    public void checkClientIp(String clientIp) {
        String ip = clientIp == null || clientIp.isBlank()
                ? "unknown"
                : clientIp.trim();

        Bucket bucket = ipBucketManager.getProxy(
                "ip:" + ip,
                this::createIpBucketConfiguration
        );

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            throw new LoginRateLimitExceededException(
                    retryAfterSeconds(
                            Duration.ofNanos(
                                    probe.getNanosToWaitForRefill()
                            )
                    )
            );
        }
    }

    public void checkUsername(String username) {
        String key = usernameKey(username);
        FailedLoginState state = failedLogins.getIfPresent(key);

        if (state == null || state.blockedUntil() == null) {
            return;
        }

        Instant now = clock.instant();

        if (now.isBefore(state.blockedUntil())) {
            throw new LoginRateLimitExceededException(
                    retryAfterSeconds(
                            Duration.between(now, state.blockedUntil())
                    )
            );
        }
    }

    public void recordFailedLogin(String username) {
        String key = usernameKey(username);
        Instant now = clock.instant();

        failedLogins.asMap().compute(key, (ignored, previous) -> {
            if (previous == null) {
                return newFailureState(1, now, now);
            }

            // Um bloqueio ativo deve ser preservado sem prorrogar sua duração.
            if (previous.blockedUntil() != null) {
                if (now.isBefore(previous.blockedUntil())) {
                    return previous;
                }

                // O bloqueio anterior expirou: inicia uma nova sequência.
                return newFailureState(1, now, now);
            }

            // A janela de falhas expirou: inicia uma nova sequência.
            if (!now.isBefore(
                    previous.windowStartedAt().plus(properties.failureWindow())
            )) {
                return newFailureState(1, now, now);
            }

            int failures = previous.failures() + 1;

            return newFailureState(
                    failures,
                    previous.windowStartedAt(),
                    now
            );
        });
    }

    public void recordSuccessfulLogin(String username) {
        failedLogins.invalidate(usernameKey(username));
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

    private FailedLoginState newFailureState(
            int failures,
            Instant windowStartedAt,
            Instant now
    ) {
        Instant blockedUntil = failures >= properties.maxFailedAttempts()
                ? now.plus(properties.lockDuration())
                : null;

        return new FailedLoginState(
                failures,
                windowStartedAt,
                blockedUntil
        );
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

    private String usernameKey(String username) {
        return "username:" + username.toLowerCase(Locale.ROOT);
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

    private record FailedLoginState(
            int failures,
            Instant windowStartedAt,
            Instant blockedUntil
    ) {
    }
}