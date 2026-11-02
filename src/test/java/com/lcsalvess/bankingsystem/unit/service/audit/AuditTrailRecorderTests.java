package com.lcsalvess.bankingsystem.unit.service.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.security.RateLimitScope;
import com.lcsalvess.bankingsystem.service.audit.AuditService;
import com.lcsalvess.bankingsystem.service.audit.AuditTrailRecorder;
import com.lcsalvess.bankingsystem.service.audit.DetachedAuditRecord;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
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

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        nanos = new AtomicLong();
        recorder = new AuditTrailRecorder(
                auditService,
                meterRegistry,
                nanos::get
        );
    }

    private static User user() {
        User user = new User(
                "lucas",
                "lucas@example.com",
                "hash",
                Role.EMPLOYEE
        );
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    private DetachedAuditRecord lastRecord(int expectedTimes) {
        ArgumentCaptor<DetachedAuditRecord> captor =
                ArgumentCaptor.forClass(DetachedAuditRecord.class);

        verify(auditService, times(expectedTimes))
                .recordDetached(captor.capture());

        return captor.getValue();
    }

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }

    @Test
    @DisplayName("Should record a successful login for the user")
    void shouldRecordLoginSuccess() {
        recorder.recordLoginSuccess(user(), IP);

        DetachedAuditRecord record = lastRecord(1);

        assertEquals(AuditEventType.LOGIN_SUCCESS, record.eventType());
        assertEquals(AuditOutcome.SUCCESS, record.outcome());
        assertEquals(AuditActorType.USER, record.actorType());
        assertEquals(7L, record.actorUserId());
        assertEquals("lucas", record.actorUsername());
        assertEquals(AuditEntityType.AUTHENTICATION, record.entityType());
        assertEquals("lucas", record.entityId());
        assertEquals(IP, record.ipAddress().getHostAddress());
        assertNull(record.reason());
    }

    @Test
    @DisplayName("Should record a failed login as anonymous with the attempted username")
    void shouldRecordLoginFailure() {
        recorder.recordLoginFailure("ghost", IP, "INVALID_CREDENTIALS");

        DetachedAuditRecord record = lastRecord(1);

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

        DetachedAuditRecord record = lastRecord(1);

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

        DetachedAuditRecord record = lastRecord(1);

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

        DetachedAuditRecord record = lastRecord(1);

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

        DetachedAuditRecord record = lastRecord(1);

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

        DetachedAuditRecord record = lastRecord(1);

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
                user(),
                "POST",
                "/api/v1/clients",
                IP,
                201,
                null,
                correlationId
        );

        DetachedAuditRecord record = lastRecord(1);

        assertEquals(
                AuditEventType.HTTP_REQUEST_COMPLETED,
                record.eventType()
        );
        assertEquals(AuditOutcome.SUCCESS, record.outcome());
        assertEquals(AuditActorType.USER, record.actorType());
        assertEquals(7L, record.actorUserId());
        assertEquals("lucas", record.actorUsername());
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

        DetachedAuditRecord record = lastRecord(1);

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
                user(),
                "POST",
                "/api/v1/transactions/withdraw",
                IP,
                409,
                "INSUFFICIENT_BALANCE",
                correlationId
        );

        DetachedAuditRecord record = lastRecord(1);

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
                user(),
                "GET",
                "/api/v1/clients",
                IP,
                503,
                null,
                correlationId
        );

        DetachedAuditRecord record = lastRecord(1);

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
        recorder.recordLoginSuccess(user(), IP);

        assertEquals(0.0, counter("audit.detached.write.failures"));
        verify(auditService, times(1)).recordDetached(any());
    }
}
