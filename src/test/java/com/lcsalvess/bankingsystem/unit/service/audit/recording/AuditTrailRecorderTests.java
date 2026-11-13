package com.lcsalvess.bankingsystem.unit.service.audit.recording;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.security.RateLimitScope;
import com.lcsalvess.bankingsystem.service.audit.recording.AuditService;
import com.lcsalvess.bankingsystem.service.audit.recording.AuditTrailRecorder;
import com.lcsalvess.bankingsystem.service.audit.recording.DetachedAuditRecord;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditTrailRecorder")
class AuditTrailRecorderTests {

    private static final String IP = "192.0.2.10";

    @Mock
    private AuditService auditService;

    private SimpleMeterRegistry meterRegistry;
    private AtomicLong nanos;
    private AuditTrailRecorder recorder;
    private User actor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        nanos = new AtomicLong();
        recorder = new AuditTrailRecorder(
                auditService,
                meterRegistry,
                nanos::get
        );

        actor = UserTestFactory.aUser()
                .withId(7L)
                .build();
    }

    private DetachedAuditRecord lastRecord() {
        ArgumentCaptor<DetachedAuditRecord> captor =
                ArgumentCaptor.forClass(DetachedAuditRecord.class);

        verify(auditService).recordDetached(captor.capture());

        return captor.getValue();
    }

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }

    @Test
    @DisplayName("Should record a successful login for the user")
    void shouldRecordLoginSuccess() {
        recorder.recordLoginSuccess(actor, IP);

        DetachedAuditRecord record = lastRecord();

        assertEquals(AuditEventType.LOGIN_SUCCESS, record.eventType());
        assertEquals(AuditOutcome.SUCCESS, record.outcome());
        assertEquals(AuditActorType.USER, record.actorType());
        assertEquals(7L, record.actorUserId());
        assertEquals(actor.getUsername(), record.actorUsername());
        assertEquals(AuditEntityType.AUTHENTICATION, record.entityType());
        assertEquals(actor.getUsername(), record.entityId());
        assertEquals(IP, record.ipAddress().getHostAddress());
        assertNull(record.reason());
    }

    @Test
    @DisplayName("Should record a failed login as anonymous with the attempted username")
    void shouldRecordLoginFailure() {
        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");

        DetachedAuditRecord record = lastRecord();

        assertEquals(AuditEventType.LOGIN_FAILURE, record.eventType());
        assertEquals(AuditOutcome.FAILURE, record.outcome());
        assertEquals(AuditActorType.ANONYMOUS, record.actorType());
        assertNull(record.actorUserId());
        assertEquals("ghost", record.actorUsername());
        assertEquals("ghost", record.entityId());
        assertEquals("INVALID_CREDENTIALS", record.reason());
        assertEquals(IP, record.ipAddress().getHostAddress());
    }

    @Test
    @DisplayName("Should record the first block with the rate limit scope as reason")
    void shouldRecordLoginBlocked() {
        recorder.recordLoginBlocked(
                "ghost",
                IP,
                RateLimitScope.USERNAME_AND_IP
        );

        DetachedAuditRecord record = lastRecord();

        assertEquals(AuditEventType.LOGIN_BLOCKED, record.eventType());
        assertEquals(AuditOutcome.DENIED, record.outcome());
        assertEquals("RATE_LIMIT_USERNAME_AND_IP", record.reason());
        assertEquals(429, record.httpStatus());
        assertEquals(IP, record.ipAddress().getHostAddress());
    }

    @Test
    @DisplayName("Should parse an IPv4 literal into an InetAddress")
    void shouldParseIpv4Address() {
        recorder.recordLoginFailure(
                "ghost",
                IP,
                "INVALID_CREDENTIALS"
        );

        DetachedAuditRecord record = lastRecord();

        assertArrayEquals(
                new byte[]{
                        (byte) 192,
                        0,
                        2,
                        10
                },
                record.ipAddress().getAddress()
        );
    }

    @Test
    @DisplayName("Should parse an IPv6 literal into an InetAddress")
    void shouldParseIpv6Address() {
        recorder.recordLoginFailure(
                "ghost",
                "2001:db8::1",
                "INVALID_CREDENTIALS"
        );

        DetachedAuditRecord record = lastRecord();

        assertArrayEquals(
                new byte[]{
                        (byte) 0x20,
                        (byte) 0x01,
                        (byte) 0x0d,
                        (byte) 0xb8,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 1
                },
                record.ipAddress().getAddress()
        );
    }

    @ParameterizedTest(name = "[{index}] invalid IP = \"{0}\"")
    @ValueSource(strings = {
            "1.2.3.4, 5.6.7.8",
            "<script>",
            "a b",
            "1234567890123456789012345678901234567890123456",
            "999.999.999.999",
            "192.0.2.10/24",
            "localhost",
            "example.com",
            ""
    })
    @DisplayName("Should discard invalid IP addresses without failing the audit write")
    void shouldDiscardInvalidIp(String ip) {
        assertDoesNotThrow(
                () -> recorder.recordLoginFailure(
                        "ghost",
                        ip,
                        "INVALID_CREDENTIALS"
                )
        );

        DetachedAuditRecord record = lastRecord();

        assertNull(record.ipAddress());
        assertEquals("INVALID_CREDENTIALS", record.reason());
    }

    @Test
    @DisplayName("Should preserve a missing IP address")
    void shouldPreserveNullIpAddress() {
        recorder.recordLoginFailure(
                "ghost",
                null,
                "INVALID_CREDENTIALS"
        );

        DetachedAuditRecord record = lastRecord();

        assertNull(record.ipAddress());
        assertEquals("INVALID_CREDENTIALS", record.reason());
    }

    @Test
    @DisplayName("Should record a repeated block of the same subject only once per window")
    void shouldDeduplicateBlocksWithinWindow() {
        recorder.recordLoginBlocked(
                "ghost",
                IP,
                RateLimitScope.USERNAME_AND_IP
        );
        recorder.recordLoginBlocked(
                "ghost",
                IP,
                RateLimitScope.USERNAME_AND_IP
        );
        recorder.recordLoginBlocked(
                "ghost",
                IP,
                RateLimitScope.USERNAME_AND_IP
        );

        verify(auditService, times(1)).recordDetached(any());
        assertEquals(2.0, counter("audit.detached.deduplicated"));
    }

    @Test
    @DisplayName("Should record the block again after the window expires")
    void shouldRecordAgainAfterWindow() {
        recorder.recordLoginBlocked(
                "ghost",
                IP,
                RateLimitScope.USERNAME
        );

        nanos.addAndGet(Duration.ofSeconds(61).toNanos());

        recorder.recordLoginBlocked(
                "ghost",
                IP,
                RateLimitScope.USERNAME
        );

        verify(auditService, times(2)).recordDetached(any());
    }

    @Test
    @DisplayName("Should not let a client IP block be bypassed by changing the username")
    void shouldDeduplicateClientIpBlockAcrossUsernames() {
        recorder.recordLoginBlocked("a", IP, RateLimitScope.CLIENT_IP);
        recorder.recordLoginBlocked("b", IP, RateLimitScope.CLIENT_IP);
        recorder.recordLoginBlocked("c", IP, RateLimitScope.CLIENT_IP);

        verify(auditService, times(1)).recordDetached(any());
    }

    @Test
    @DisplayName("Should keep blocks of different subjects apart")
    void shouldNotDeduplicateDifferentSubjects() {
        recorder.recordLoginBlocked("a", IP, RateLimitScope.USERNAME);
        recorder.recordLoginBlocked("b", IP, RateLimitScope.USERNAME);
        recorder.recordLoginBlocked(
                "a",
                "192.0.2.11",
                RateLimitScope.USERNAME_AND_IP
        );
        recorder.recordLoginBlocked(
                "a",
                "192.0.2.12",
                RateLimitScope.USERNAME_AND_IP
        );
        recorder.recordLoginBlocked("a", IP, RateLimitScope.USER_ID);

        verify(auditService, times(5)).recordDetached(any());
    }

    @Test
    @DisplayName("Should retry a blocked event after a failed audit write")
    void shouldRetryBlockedEventAfterWriteFailure() {
        doThrow(new IllegalStateException("database down"))
                .doNothing()
                .when(auditService).recordDetached(any());

        // First attempt fails and releases its deduplication reservation.
        recorder.recordLoginBlocked(
                "ghost",
                IP,
                RateLimitScope.USERNAME
        );

        // Second attempt retries and succeeds.
        recorder.recordLoginBlocked(
                "ghost",
                IP,
                RateLimitScope.USERNAME
        );

        // Third attempt is deduplicated after the successful write.
        recorder.recordLoginBlocked(
                "ghost",
                IP,
                RateLimitScope.USERNAME
        );

        verify(auditService, times(2)).recordDetached(any());

        assertEquals(1.0, counter("audit.detached.write.failures"));
        assertEquals(1.0, counter("audit.detached.deduplicated"));
    }

    @Test
    @DisplayName("Should record a successful HTTP response for an authenticated user")
    void shouldRecordSuccessfulHttpResponse() {
        UUID correlationId = UUID.randomUUID();

        recorder.recordHttpRequestCompleted(
                actor,
                "POST",
                "/api/v1/clients",
                IP,
                201,
                null,
                correlationId
        );

        DetachedAuditRecord record = lastRecord();

        assertEquals(
                AuditEventType.HTTP_REQUEST_COMPLETED,
                record.eventType()
        );
        assertEquals(AuditOutcome.SUCCESS, record.outcome());
        assertEquals(AuditActorType.USER, record.actorType());
        assertEquals(7L, record.actorUserId());
        assertEquals(actor.getUsername(), record.actorUsername());
        assertEquals(AuditEntityType.ENDPOINT, record.entityType());
        assertEquals("POST /api/v1/clients", record.entityId());
        assertEquals(201, record.httpStatus());
        assertNull(record.reason());
        assertEquals(IP, record.ipAddress().getHostAddress());
        assertEquals(correlationId, record.correlationId());
    }

    @Test
    @DisplayName("Should record an unauthorized HTTP response for an anonymous request")
    void shouldRecordUnauthorizedHttpResponse() {
        UUID correlationId = UUID.randomUUID();

        recorder.recordHttpRequestCompleted(
                null,
                "POST",
                "/api/v1/auth/login",
                IP,
                401,
                null,
                correlationId
        );

        DetachedAuditRecord record = lastRecord();

        assertEquals(
                AuditEventType.HTTP_REQUEST_COMPLETED,
                record.eventType()
        );
        assertEquals(AuditOutcome.DENIED, record.outcome());
        assertEquals(AuditActorType.ANONYMOUS, record.actorType());
        assertNull(record.actorUserId());
        assertNull(record.actorUsername());
        assertEquals("UNAUTHORIZED", record.reason());
        assertEquals(401, record.httpStatus());
        assertEquals(correlationId, record.correlationId());
    }

    @Test
    @DisplayName("Should preserve the business failure reason in the HTTP audit event")
    void shouldPreserveBusinessFailureReason() {
        UUID correlationId = UUID.randomUUID();

        recorder.recordHttpRequestCompleted(
                actor,
                "POST",
                "/api/v1/transactions/withdrawals",
                IP,
                409,
                "INSUFFICIENT_BALANCE",
                correlationId
        );

        DetachedAuditRecord record = lastRecord();

        assertEquals(
                AuditEventType.HTTP_REQUEST_COMPLETED,
                record.eventType()
        );
        assertEquals(AuditOutcome.FAILURE, record.outcome());
        assertEquals("INSUFFICIENT_BALANCE", record.reason());
        assertEquals(409, record.httpStatus());
        assertEquals(correlationId, record.correlationId());
    }

    @Test
    @DisplayName("Should use a generic reason for an HTTP server error")
    void shouldRecordHttpServerError() {
        UUID correlationId = UUID.randomUUID();

        recorder.recordHttpRequestCompleted(
                actor,
                "GET",
                "/api/v1/clients",
                IP,
                503,
                null,
                correlationId
        );

        DetachedAuditRecord record = lastRecord();

        assertEquals(
                AuditEventType.HTTP_REQUEST_COMPLETED,
                record.eventType()
        );
        assertEquals(AuditOutcome.FAILURE, record.outcome());
        assertEquals("HTTP_SERVER_ERROR", record.reason());
        assertEquals(503, record.httpStatus());
        assertEquals(correlationId, record.correlationId());
    }

    @Test
    @DisplayName("Should swallow a write failure and count it")
    void shouldSwallowWriteFailure() {
        doThrow(new IllegalStateException("database down"))
                .when(auditService).recordDetached(any());

        assertDoesNotThrow(
                () -> recorder.recordLoginFailure(
                        "ghost",
                        IP,
                        "INVALID_CREDENTIALS"
                )
        );

        assertEquals(1.0, counter("audit.detached.write.failures"));
    }

    @Test
    @DisplayName("Should not count a failure when the write succeeds")
    void shouldNotCountFailureOnSuccess() {
        recorder.recordLoginSuccess(actor, IP);

        assertEquals(0.0, counter("audit.detached.write.failures"));
        verify(auditService, times(1)).recordDetached(any());
    }

    @Test
    @DisplayName("Should ignore username case when deduplicating blocks")
    void shouldDeduplicateBlocksIgnoringUsernameCase() {
        recorder.recordLoginBlocked("Ghost", IP, RateLimitScope.USERNAME);
        recorder.recordLoginBlocked("GHOST", IP, RateLimitScope.USERNAME);
        recorder.recordLoginBlocked("ghost", IP, RateLimitScope.USERNAME);

        verify(auditService, times(1)).recordDetached(any());
        assertEquals(2.0, counter("audit.detached.deduplicated"));
    }

    @Test
    @DisplayName("Should record a repeated client error only once per window")
    void shouldDeduplicateRepeatedHttpClientErrors() {
        for (int i = 0; i < 3; i++) {
            recorder.recordHttpRequestCompleted(
                    null, "POST", "/api/v1/auth/login", IP, 400, null, null
            );
        }

        verify(auditService, times(1)).recordDetached(any());
        assertEquals(2.0, counter("audit.detached.deduplicated"));

        nanos.addAndGet(Duration.ofSeconds(61).toNanos());

        recorder.recordHttpRequestCompleted(
                null, "POST", "/api/v1/auth/login", IP, 400, null, null
        );

        verify(auditService, times(2)).recordDetached(any());
    }

    @Test
    @DisplayName("Should keep client errors of different subjects apart")
    void shouldNotDeduplicateDifferentHttpFailures() {
        recorder.recordHttpRequestCompleted(
                actor, "GET", "/api/v1/users/{id}", IP, 403, null, null
        );
        recorder.recordHttpRequestCompleted(
                actor, "GET", "/api/v1/users/{id}", "192.0.2.11", 403, null, null
        );
        recorder.recordHttpRequestCompleted(
                actor, "GET", "/api/v1/users/{id}", IP, 404, null, null
        );

        verify(auditService, times(3)).recordDetached(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 401, 500})
    @DisplayName("Should never deduplicate successes, 401 or server errors")
    void shouldNotDeduplicateOtherHttpOutcomes(int status) {
        recorder.recordHttpRequestCompleted(
                actor, "POST", "/api/v1/auth/login", IP, status, null, null
        );
        recorder.recordHttpRequestCompleted(
                actor, "POST", "/api/v1/auth/login", IP, status, null, null
        );

        verify(auditService, times(2)).recordDetached(any());
    }

    @Test
    @DisplayName("Should skip writes briefly after an infrastructure failure")
    void shouldBackOffAfterInfrastructureFailure() {
        doThrow(new CannotCreateTransactionException("database down"))
                .doNothing()
                .when(auditService).recordDetached(any());

        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");
        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");

        verify(auditService, times(1)).recordDetached(any());
        assertEquals(2.0, counter("audit.detached.write.failures"));

        nanos.addAndGet(Duration.ofSeconds(6).toNanos());

        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");

        verify(auditService, times(2)).recordDetached(any());
    }

    @Test
    @DisplayName("Should not back off after a failure caused by the record")
    void shouldNotBackOffAfterNonInfrastructureFailure() {
        doThrow(new IllegalStateException("constraint"))
                .doNothing()
                .when(auditService).recordDetached(any());

        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");
        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");

        verify(auditService, times(2)).recordDetached(any());
    }

    @Test
    @DisplayName("Should release the HTTP failure deduplication reservation after a write failure")
    void shouldRetryHttpFailureAfterWriteFailure() {
        doThrow(new IllegalStateException("audit write failed"))
                .doNothing()
                .when(auditService).recordDetached(any());

        recorder.recordHttpRequestCompleted(
                null,
                "POST",
                "/api/v1/clients",
                IP,
                400,
                null,
                null
        );

        recorder.recordHttpRequestCompleted(
                null,
                "POST",
                "/api/v1/clients",
                IP,
                400,
                null,
                null
        );

        verify(auditService, times(2)).recordDetached(any());
        assertEquals(1.0, counter("audit.detached.write.failures"));
        assertEquals(0.0, counter("audit.detached.deduplicated"));
    }

    @Test
    @DisplayName("Should discard an IPv6 literal that cannot be parsed")
    void shouldDiscardMalformedIpv6Address() {
        recorder.recordLoginFailure(
                "ghost",
                "::::",
                "INVALID_CREDENTIALS"
        );

        DetachedAuditRecord record = lastRecord();

        assertNull(record.ipAddress());
        assertEquals("INVALID_CREDENTIALS", record.reason());
    }

    @Test
    @DisplayName("Should use the original client IP value when an HTTP failure IP is invalid")
    void shouldUseOriginalIpForHttpFailureDeduplicationWhenParsingFails() {
        recorder.recordHttpRequestCompleted(
                null,
                "POST",
                "/api/v1/clients",
                "invalid-host",
                400,
                null,
                null
        );

        recorder.recordHttpRequestCompleted(
                null,
                "POST",
                "/api/v1/clients",
                "invalid-host",
                400,
                null,
                null
        );

        verify(auditService, times(1)).recordDetached(any());
        assertEquals(1.0, counter("audit.detached.deduplicated"));

        DetachedAuditRecord record = lastRecord();
        assertNull(record.ipAddress());
    }

    @Test
    @DisplayName("Should use the original client IP value when a blocked login IP is invalid")
    void shouldUseOriginalIpForBlockedLoginWhenParsingFails() {
        recorder.recordLoginBlocked(
                "ghost",
                "invalid-host",
                RateLimitScope.USERNAME
        );

        DetachedAuditRecord record = lastRecord();

        assertNull(record.ipAddress());
        assertEquals("RATE_LIMIT_USERNAME", record.reason());
    }

    @Test
    @DisplayName("Should start infrastructure backoff after a data access resource failure")
    void shouldBackOffAfterDataAccessResourceFailure() {
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .doNothing()
                .when(auditService).recordDetached(any());

        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");
        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");

        verify(auditService, times(1)).recordDetached(any());
        assertEquals(2.0, counter("audit.detached.write.failures"));

        nanos.addAndGet(Duration.ofSeconds(6).toNanos());

        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");

        verify(auditService, times(2)).recordDetached(any());
    }

    @Test
    @DisplayName("Should start infrastructure backoff after a query timeout")
    void shouldBackOffAfterQueryTimeout() {
        doThrow(new QueryTimeoutException("query timed out"))
                .doNothing()
                .when(auditService).recordDetached(any());

        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");
        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");

        verify(auditService, times(1)).recordDetached(any());
        assertEquals(2.0, counter("audit.detached.write.failures"));

        nanos.addAndGet(Duration.ofSeconds(6).toNanos());

        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");

        verify(auditService, times(2)).recordDetached(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 429})
    @DisplayName("Should classify forbidden and rate-limited HTTP responses as denied")
    void shouldClassifyDeniedHttpResponses(int status) {
        recorder.recordHttpRequestCompleted(
                null,
                "GET",
                "/api/v1/resource",
                IP,
                status,
                null,
                null
        );

        DetachedAuditRecord record = lastRecord();

        assertEquals(AuditOutcome.DENIED, record.outcome());
        assertEquals(status, record.httpStatus());

        if (status == 429) {
            assertEquals("RATE_LIMIT_EXCEEDED", record.reason());
        } else {
            assertEquals("FORBIDDEN", record.reason());
        }
    }

    @Test
    @DisplayName("Should use an unexpected status reason for a nonstandard HTTP status")
    void shouldUseUnexpectedStatusReason() {
        recorder.recordHttpRequestCompleted(
                null,
                "GET",
                "/api/v1/resource",
                IP,
                199,
                null,
                null
        );

        DetachedAuditRecord record = lastRecord();

        assertEquals(AuditOutcome.FAILURE, record.outcome());
        assertEquals("HTTP_UNEXPECTED_STATUS", record.reason());
        assertEquals(199, record.httpStatus());
    }

    @Test
    @DisplayName("Should discard IPv6 candidates containing invalid characters")
    void shouldDiscardIpv6CandidateContainingInvalidCharacters() {
        recorder.recordLoginFailure(
                "ghost",
                "2001:db8::g",
                "INVALID_CREDENTIALS"
        );

        DetachedAuditRecord record = lastRecord();

        assertNull(record.ipAddress());
        assertEquals("INVALID_CREDENTIALS", record.reason());
    }

    @Test
    @DisplayName("Should use generic reason when the supplied failure reason is blank")
    void shouldUseGenericReasonWhenFailureReasonIsBlank() {
        recorder.recordHttpRequestCompleted(
                actor,
                "POST",
                "/api/v1/clients",
                IP,
                400,
                "   ",
                null
        );

        DetachedAuditRecord record = lastRecord();

        assertEquals(AuditOutcome.FAILURE, record.outcome());
        assertEquals("HTTP_CLIENT_ERROR", record.reason());
    }

    @Test
    @DisplayName("Should record a daily yield run as a system event")
    void shouldRecordYieldRun() {
        recorder.recordYieldRun(
                LocalDate.of(2026, 11, 13),
                AuditOutcome.FAILURE,
                "COMPLETED_WITH_FAILURES"
        );

        DetachedAuditRecord record = lastRecord();

        assertEquals(AuditEventType.YIELD_RUN, record.eventType());
        assertEquals(AuditOutcome.FAILURE, record.outcome());
        assertEquals(AuditActorType.SYSTEM, record.actorType());
        assertNull(record.actorUserId());
        assertNull(record.actorUsername());
        assertEquals(AuditEntityType.YIELD_RUN, record.entityType());
        assertEquals("2026-11-13", record.entityId());
        assertEquals("COMPLETED_WITH_FAILURES", record.reason());
        assertNull(record.ipAddress());
        assertNull(record.httpStatus());
        assertNull(record.correlationId());
    }
}
