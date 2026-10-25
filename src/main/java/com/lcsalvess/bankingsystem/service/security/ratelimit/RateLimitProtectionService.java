package com.lcsalvess.bankingsystem.service.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.exception.security.RateLimitExceededException;
import com.lcsalvess.bankingsystem.exception.security.RateLimitOperation;
import com.lcsalvess.bankingsystem.exception.security.RateLimitScope;
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
 * Applies rate limits to sensitive API operations.
 *
 * <p>Login attempts are limited independently by client IP, username and IP,
 * and username alone. Password change attempts are limited per authenticated
 * user.
 *
 * <p>IP limits use Bucket4j. Login attempt counters and password change
 * attempt counters use state maintained by Caffeine.
 *
 * <p>Attempt counters are updated atomically before password verification.
 * Successful operations can reset their associated counters, while
 * infrastructure failures can cancel attempts that could not be evaluated.
 *
 * <p>The state is held in memory, is not shared between application instances,
 * and may be lost on restart or when entries are evicted from the cache.
 */
@Service
public class RateLimitProtectionService {

    private static final Logger log =
            LoggerFactory.getLogger(RateLimitProtectionService.class);

    private static final String REJECTION_METRIC =
            "security.rate_limit.rejected";

    private static final Duration BUCKET_RETENTION_AFTER_REFILL =
            Duration.ofSeconds(30);

    private static final String UNKNOWN_IP = "unknown";
    private static final String INVALID_IP = "invalid";

    // Acceptable text for IPv4/IPv6 addresses. Invalid values share one bucket
    // to avoid excessively large keys and log injection.
    private static final Pattern IP_TEXT =
            Pattern.compile("[0-9A-Za-z:.%-]{1,64}");

    private static final String USERNAME_AND_IP_PREFIX = "username-ip:";
    private static final String USERNAME_PREFIX = "username:";
    private static final String PASSWORD_CHANGE_PREFIX = "password-change:";

    private final LoginRateLimitProperties properties;
    private final Clock clock;

    private final ProxyManager<String> ipBucketManager;

    private final Cache<String, AttemptState> attemptStates;

    private final Map<RateLimitOperation, Map<RateLimitScope, Counter>>
            rejectionCounters = new EnumMap<>(RateLimitOperation.class);

    public RateLimitProtectionService(
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

        registerRejectionCounter(
                meterRegistry,
                RateLimitOperation.LOGIN,
                RateLimitScope.CLIENT_IP
        );

        registerRejectionCounter(
                meterRegistry,
                RateLimitOperation.LOGIN,
                RateLimitScope.USERNAME_AND_IP
        );

        registerRejectionCounter(
                meterRegistry,
                RateLimitOperation.LOGIN,
                RateLimitScope.USERNAME
        );

        registerRejectionCounter(
                meterRegistry,
                RateLimitOperation.PASSWORD_CHANGE,
                RateLimitScope.USER_ID
        );
    }

    /**
     * Registers a rejection counter for a specific operation and scope.
     */
    private void registerRejectionCounter(
            MeterRegistry meterRegistry,
            RateLimitOperation operation,
            RateLimitScope scope
    ) {
        Counter counter = Counter.builder(REJECTION_METRIC)
                .description("Requests rejected by the security rate limiter")
                .tag("operation", operation.metricTag())
                .tag("scope", scope.metricTag())
                .register(meterRegistry);

        rejectionCounters
                .computeIfAbsent(
                        operation,
                        ignored -> new EnumMap<>(RateLimitScope.class)
                )
                .put(scope, counter);
    }

    /**
     * Admits a login attempt or throws {@link RateLimitExceededException}.
     *
     * <p>Consumes an IP token and records the attempt for both the
     * username/IP combination and the username. The caller should invoke
     * {@link #recordSuccessfulLogin(LoginAttempt)} when the credentials
     * are valid, or {@link #cancelAttempt(LoginAttempt)} if verification
     * cannot be completed because of an infrastructure failure.
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
                RateLimitOperation.LOGIN,
                RateLimitScope.USERNAME_AND_IP,
                ip,
                now
        );

        Instant usernameWindowStartedAt;

        try {
            usernameWindowStartedAt = reserve(
                    usernameKey,
                    properties.maxFailedAttemptsPerUsername(),
                    RateLimitOperation.LOGIN,
                    RateLimitScope.USERNAME,
                    ip,
                    now
            );
        } catch (RateLimitExceededException exception) {
            // The attempt was not admitted: it must not count against
            // the username/IP combination.
            release(
                    usernameAndIpKey,
                    usernameAndIpWindowStartedAt
            );
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
     * Resets the username and username/IP counters after a successful login.
     */
    public void recordSuccessfulLogin(LoginAttempt attempt) {
        attemptStates.invalidate(attempt.usernameAndIpKey());
        attemptStates.invalidate(attempt.usernameKey());
    }

    /**
     * Cancels an attempt that could not be evaluated, such as an
     * infrastructure failure. Does not alter a newer attempt sequence.
     */
    public void cancelAttempt(LoginAttempt attempt) {
        release(
                attempt.usernameAndIpKey(),
                attempt.usernameAndIpWindowStartedAt()
        );

        release(
                attempt.usernameKey(),
                attempt.usernameWindowStartedAt()
        );
    }

    /**
     * Admits a password change attempt for an authenticated user or throws
     * {@link RateLimitExceededException}.
     *
     * <p>Uses a separate key for each user, independently of login counters.
     * The caller should invoke
     * {@link #recordSuccessfulCurrentPasswordVerification(PasswordChangeAttempt)}
     * when the current password is confirmed, or
     * {@link #cancelPasswordChangeAttempt(PasswordChangeAttempt)} if
     * verification cannot be completed because of an infrastructure failure.
     */
    public PasswordChangeAttempt beginPasswordChangeAttempt(long userId) {
        String key = PASSWORD_CHANGE_PREFIX + userId;

        Instant windowStartedAt = reserve(
                key,
                properties.maxFailedAttempts(),
                RateLimitOperation.PASSWORD_CHANGE,
                RateLimitScope.USER_ID,
                "user:" + userId,
                clock.instant()
        );

        return new PasswordChangeAttempt(key, windowStartedAt);
    }

    /**
     * Resets the password change counter after the current password
     * has been successfully verified.
     */
    public void recordSuccessfulCurrentPasswordVerification(
            PasswordChangeAttempt attempt
    ) {
        attemptStates.invalidate(attempt.key());
    }

    /**
     * Cancels a password change attempt that could not be evaluated.
     * Does not alter a newer attempt sequence.
     */
    public void cancelPasswordChangeAttempt(PasswordChangeAttempt attempt) {
        release(attempt.key(), attempt.windowStartedAt());
    }

    /**
     * Removes counters and blocks for a username across all IP addresses.
     *
     * <p>This method traverses in-memory keys and is intended for occasional
     * operations, such as administrative unlocking or tests, rather than
     * the normal login flow.
     */
    public void clearFailures(String username) {
        String normalizedUsername = normalizeUsername(username);
        String prefix = usernameAndIpKey(normalizedUsername, "");

        attemptStates.invalidate(usernameKey(normalizedUsername));

        attemptStates.asMap()
                .keySet()
                .removeIf(key -> key.startsWith(prefix));
    }

    private void consumeIpToken(String ip) {
        Bucket bucket = ipBucketManager.getProxy(
                "ip:" + ip,
                this::createIpBucketConfiguration
        );

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            throw rejection(
                    RateLimitOperation.LOGIN,
                    RateLimitScope.CLIENT_IP,
                    Duration.ofNanos(probe.getNanosToWaitForRefill()),
                    ip
            );
        }
    }

    /**
     * Atomically records an attempt for the supplied key and returns the
     * beginning of the window in which it was recorded.
     *
     * <p>Throws if the key is already blocked. The attempt that reaches the
     * configured limit is still evaluated; subsequent attempts are rejected.
     */
    private Instant reserve(
            String key,
            int limit,
            RateLimitOperation operation,
            RateLimitScope scope,
            String subject,
            Instant now
    ) {
        AtomicReference<Instant> windowStartedAt = new AtomicReference<>();

        attemptStates.asMap().compute(key, (ignored, previous) -> {
            AttemptState current = activeState(previous, now);

            // Reject an active block without changing or extending it.
            if (current != null && current.blockedUntil() != null) {
                throw rejection(
                        operation,
                        scope,
                        Duration.between(now, current.blockedUntil()),
                        subject
                );
            }

            Instant started =
                    current == null ? now : current.windowStartedAt();

            int attempts =
                    current == null ? 1 : current.attempts() + 1;

            Instant blockedUntil = attempts >= limit
                    ? now.plus(properties.lockDuration())
                    : null;

            windowStartedAt.set(started);

            return new AttemptState(
                    attempts,
                    started,
                    blockedUntil
            );
        });

        return windowStartedAt.get();
    }

    private void release(String key, Instant windowStartedAt) {
        attemptStates.asMap().computeIfPresent(key, (ignored, state) -> {
            // A newer sequence does not belong to this attempt.
            if (!state.windowStartedAt().equals(windowStartedAt)) {
                return state;
            }

            int attempts = state.attempts() - 1;

            if (attempts < 1) {
                return null;
            }

            return new AttemptState(
                    attempts,
                    state.windowStartedAt(),
                    null
            );
        });
    }

    /**
     * Returns the state if it is still active, or null if its block or
     * failure window has expired and a new sequence should begin.
     */
    private AttemptState activeState(
            AttemptState previous,
            Instant now
    ) {
        if (previous == null) {
            return null;
        }

        if (previous.blockedUntil() != null) {
            return now.isBefore(previous.blockedUntil())
                    ? previous
                    : null;
        }

        boolean windowExpired = !now.isBefore(
                previous.windowStartedAt()
                        .plus(properties.failureWindow())
        );

        return windowExpired ? null : previous;
    }

    private RateLimitExceededException rejection(
            RateLimitOperation operation,
            RateLimitScope scope,
            Duration wait,
            String subject
    ) {
        Map<RateLimitScope, Counter> counters =
                rejectionCounters.get(operation);

        Counter counter = counters == null ? null : counters.get(scope);

        if (counter == null) {
            throw new IllegalStateException(
                    "No rejection counter registered for operation "
                            + operation + " and scope " + scope
            );
        }

        counter.increment();

        log.warn(
                "Request rejected: rate limit exceeded. "
                        + "operation={}, scope={}, subject={}",
                operation.metricTag(),
                scope.metricTag(),
                subject
        );

        return new RateLimitExceededException(
                retryAfterSeconds(wait),
                operation,
                scope
        );
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

    private static String usernameAndIpKey(
            String normalizedUsername,
            String ip
    ) {
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