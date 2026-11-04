package com.lcsalvess.bankingsystem.service.audit.recording;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.security.RateLimitScope;
import com.lcsalvess.bankingsystem.util.normalization.UsernameNormalizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Entry point for security and failure events recorded in the audit trail.
 *
 * <p>Unlike {@link AuditService#recordSuccess}, which is fail-closed and
 * participates in the operation's transaction, writes performed here are
 * best-effort: if an audit event cannot be written (for example, because
 * the database is unavailable), the original request failure must not be
 * masked. Write failures are logged and increment the
 * {@value #WRITE_FAILURES_METRIC} counter.
 *
 * <p>This component must never be called from within an active transaction
 * (see {@link AuditService#recordDetached}).
 */
@Component
public class AuditTrailRecorder {

    static final String WRITE_FAILURES_METRIC = "audit.detached.write.failures";
    static final String DEDUPLICATED_METRIC = "audit.detached.deduplicated";

    static final Duration BLOCKED_DEDUPLICATION_WINDOW = Duration.ofMinutes(1);
    static final long BLOCKED_DEDUPLICATION_MAX_KEYS = 10_000;

    static final Duration HTTP_FAILURE_DEDUPLICATION_WINDOW =
            Duration.ofMinutes(1);
    static final long HTTP_FAILURE_DEDUPLICATION_MAX_KEYS = 10_000;

    /**
     * After an infrastructure failure (database unavailable or timing out),
     * writes are skipped for this period so that each request does not wait
     * for its own connection timeout.
     */
    static final Duration INFRASTRUCTURE_BACKOFF = Duration.ofSeconds(5);

    private static final String BACKOFF_KEY = "audit-store-unavailable";

    private static final Logger log =
            LoggerFactory.getLogger(AuditTrailRecorder.class);

    private static final Pattern IPV4_LITERAL = Pattern.compile(
            "^(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
                    + "(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    );

    private static final Pattern IPV6_LITERAL =
            Pattern.compile("[0-9A-Fa-f:.]+");

    private final AuditService auditService;
    private final Counter writeFailures;
    private final Counter deduplicated;
    private final Cache<String, Object> recentBlocks;
    private final Cache<String, Object> recentHttpFailures;
    private final Cache<String, Boolean> infrastructureBackoff;

    @Autowired
    public AuditTrailRecorder(
            AuditService auditService,
            MeterRegistry meterRegistry
    ) {
        this(auditService, meterRegistry, Ticker.systemTicker());
    }

    /**
     * Constructor exposed for tests that need to control the deduplication clock.
     */
    public AuditTrailRecorder(
            AuditService auditService,
            MeterRegistry meterRegistry,
            Ticker ticker
    ) {
        this.auditService = auditService;

        this.writeFailures = Counter.builder(WRITE_FAILURES_METRIC)
                .description("Audit events that could not be written")
                .register(meterRegistry);

        this.deduplicated = Counter.builder(DEDUPLICATED_METRIC)
                .description("Audit events skipped as repeats within the window")
                .register(meterRegistry);

        this.recentBlocks = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterWrite(BLOCKED_DEDUPLICATION_WINDOW)
                .maximumSize(BLOCKED_DEDUPLICATION_MAX_KEYS)
                .build();

        this.recentHttpFailures = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterWrite(HTTP_FAILURE_DEDUPLICATION_WINDOW)
                .maximumSize(HTTP_FAILURE_DEDUPLICATION_MAX_KEYS)
                .build();

        this.infrastructureBackoff = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterWrite(INFRASTRUCTURE_BACKOFF)
                .maximumSize(1)
                .build();
    }

    public void recordLoginSuccess(User user, String clientIp) {
        write(new DetachedAuditRecord(
                AuditEventType.LOGIN_SUCCESS,
                AuditOutcome.SUCCESS,
                AuditActorType.USER,
                user.getId(),
                user.getUsername(),
                AuditEntityType.AUTHENTICATION,
                user.getUsername(),
                null,
                parseClientIp(clientIp),
                null,
                null
        ));
    }

    public void recordLoginFailure(
            String attemptedUsername,
            String clientIp,
            String reason
    ) {
        write(new DetachedAuditRecord(
                AuditEventType.LOGIN_FAILURE,
                AuditOutcome.FAILURE,
                AuditActorType.ANONYMOUS,
                null,
                attemptedUsername,
                AuditEntityType.AUTHENTICATION,
                attemptedUsername,
                reason,
                parseClientIp(clientIp),
                null,
                null
        ));
    }

    /**
     * A blocked client can generate an unlimited number of HTTP 429 responses.
     * Recording every occurrence would allow an attacker to control the size
     * of the append-only audit table. Therefore, only the first block for
     * each subject within the deduplication window is recorded.
     */
    public void recordLoginBlocked(
            String attemptedUsername,
            String clientIp,
            RateLimitScope scope
    ) {
        InetAddress ipAddress = parseClientIp(clientIp);

        String deduplicationIp = ipAddress != null
                ? ipAddress.getHostAddress()
                : clientIp;

        DetachedAuditRecord record = new DetachedAuditRecord(
                AuditEventType.LOGIN_BLOCKED,
                AuditOutcome.DENIED,
                AuditActorType.ANONYMOUS,
                null,
                attemptedUsername,
                AuditEntityType.AUTHENTICATION,
                attemptedUsername,
                "RATE_LIMIT_" + scope.name(),
                ipAddress,
                429,
                null
        );

        String key = blockKey(
                UsernameNormalizer.normalize(attemptedUsername),
                deduplicationIp,
                scope
        );

        Object reservationToken = new Object();

        if (recentBlocks.asMap().putIfAbsent(
                key,
                reservationToken
        ) != null) {
            deduplicated.increment();
            return;
        }

        if (!write(record)) {
            recentBlocks.asMap().remove(key, reservationToken);
        }
    }

    public void recordHttpRequestCompleted(
            User actor,
            String method,
            String path,
            String clientIp,
            int httpStatus,
            String failureReason,
            UUID correlationId
    ) {
        InetAddress ipAddress = parseClientIp(clientIp);
        String reason = reasonForHttpStatus(httpStatus, failureReason);

        DetachedAuditRecord record = new DetachedAuditRecord(
                AuditEventType.HTTP_REQUEST_COMPLETED,
                outcomeOf(httpStatus),
                actor != null
                        ? AuditActorType.USER
                        : AuditActorType.ANONYMOUS,
                actor != null ? actor.getId() : null,
                actor != null ? actor.getUsername() : null,
                AuditEntityType.ENDPOINT,
                method + " " + path,
                reason,
                ipAddress,
                httpStatus,
                correlationId
        );

        if (!isDeduplicableFailure(httpStatus)) {
            write(record);
            return;
        }

        String key = httpFailureKey(
                actor,
                ipAddress != null ? ipAddress.getHostAddress() : clientIp,
                method,
                path,
                httpStatus,
                reason
        );

        Object reservationToken = new Object();

        if (recentHttpFailures.asMap().putIfAbsent(
                key,
                reservationToken
        ) != null) {
            deduplicated.increment();
            return;
        }

        if (!write(record)) {
            recentHttpFailures.asMap().remove(key, reservationToken);
        }
    }

    /**
     * Client errors can be produced at will, and the audit table is
     * append-only. Only the first occurrence of the same failure (actor, IP,
     * endpoint, status, reason) within the window is recorded.
     *
     * <p>401 is excluded: it is only recorded for the login endpoint, where
     * failed attempts are already bounded by the rate limiter and each one
     * has its own LOGIN_FAILURE event. Successes and server errors are
     * never deduplicated.
     */
    private static boolean isDeduplicableFailure(int httpStatus) {
        return httpStatus >= 400
                && httpStatus < 500
                && httpStatus != 401;
    }

    private static String httpFailureKey(
            User actor,
            String clientIp,
            String method,
            String path,
            int httpStatus,
            String reason
    ) {
        String subject = actor != null
                ? "user:" + actor.getId()
                : "anonymous";

        return String.join(
                "|",
                subject,
                String.valueOf(clientIp),
                method,
                path,
                String.valueOf(httpStatus),
                reason
        );
    }

    private static String blockKey(
            String username,
            String clientIp,
            RateLimitScope scope
    ) {
        return switch (scope) {
            case CLIENT_IP -> scope + "|" + clientIp;
            case USERNAME -> scope + "|" + username;
            case USERNAME_AND_IP, USER_ID ->
                    scope + "|" + username + "|" + clientIp;
        };
    }

    /**
     * Converts a numeric IPv4 or IPv6 literal into an InetAddress without
     * performing hostname resolution. Missing or invalid values are omitted
     * from the audit record.
     */
    private static InetAddress parseClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }

        try {
            if (IPV4_LITERAL.matcher(clientIp).matches()) {
                String[] octets = clientIp.split("\\.");
                byte[] address = new byte[4];

                for (int i = 0; i < octets.length; i++) {
                    address[i] = (byte) Integer.parseInt(octets[i]);
                }

                return InetAddress.getByAddress(address);
            }

            if (clientIp.indexOf(':') >= 0
                    && IPV6_LITERAL.matcher(clientIp).matches()) {
                return InetAddress.getByName(clientIp);
            }
        } catch (UnknownHostException exception) {
            log.debug("Ignoring invalid IP address in audit event", exception);
            return null;
        }

        log.debug("Ignoring non-literal IP address in audit event");
        return null;
    }

    private boolean write(DetachedAuditRecord record) {
        if (infrastructureBackoff.getIfPresent(BACKOFF_KEY) != null) {
            writeFailures.increment();

            log.debug(
                    "Skipping audit event while the audit store is unavailable: "
                            + "eventType={}, outcome={}",
                    record.eventType(),
                    record.outcome()
            );

            return false;
        }

        try {
            auditService.recordDetached(record);
            return true;
        } catch (RuntimeException exception) {
            writeFailures.increment();

            if (isInfrastructureFailure(exception)) {
                infrastructureBackoff.put(BACKOFF_KEY, Boolean.TRUE);
            }

            log.error(
                    "Could not record audit event: eventType={}, outcome={}",
                    record.eventType(),
                    record.outcome(),
                    exception
            );

            return false;
        }
    }

    /**
     * Only infrastructure failures start the backoff. A failure caused by
     * the record itself must never suspend auditing of other requests.
     */
    private static boolean isInfrastructureFailure(
            RuntimeException exception
    ) {
        return exception instanceof DataAccessResourceFailureException
                || exception instanceof CannotCreateTransactionException
                || exception instanceof QueryTimeoutException;
    }

    private static AuditOutcome outcomeOf(int httpStatus) {
        if (httpStatus >= 200 && httpStatus < 400) {
            return AuditOutcome.SUCCESS;
        }

        if (httpStatus == 401
                || httpStatus == 403
                || httpStatus == 429) {
            return AuditOutcome.DENIED;
        }

        return AuditOutcome.FAILURE;
    }

    private static String reasonForHttpStatus(
            int httpStatus,
            String failureReason
    ) {
        if (httpStatus >= 200 && httpStatus < 400) {
            return null;
        }

        if (failureReason != null && !failureReason.isBlank()) {
            return failureReason;
        }

        return switch (httpStatus) {
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 429 -> "RATE_LIMIT_EXCEEDED";
            default -> {
                if (httpStatus >= 500) {
                    yield "HTTP_SERVER_ERROR";
                }

                if (httpStatus >= 400) {
                    yield "HTTP_CLIENT_ERROR";
                }

                yield "HTTP_UNEXPECTED_STATUS";
            }
        };
    }
}
