package com.lcsalvess.bankingsystem.unit.service.audit.recording;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventRepository;
import com.lcsalvess.bankingsystem.service.audit.recording.AuditService;
import com.lcsalvess.bankingsystem.service.audit.recording.DetachedAuditRecord;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService.recordDetached")
class AuditServiceRecordDetachedTests {

    private static final Instant NOW = Instant.parse("2026-11-01T13:30:00Z");

    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));

    private static final InetAddress IP = ipv4Address();

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private CurrentUserService currentUserService;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        MDC.clear();
        auditService = new AuditService(
                auditEventRepository,
                currentUserService,
                CLOCK
        );
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private static InetAddress ipv4Address() {
        try {
            return InetAddress.getByAddress(
                    new byte[]{
                            (byte) 192,
                            (byte) 0,
                            (byte) 2,
                            (byte) 10
                    }
            );
        } catch (UnknownHostException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static DetachedAuditRecord record(UUID correlationId) {
        return new DetachedAuditRecord(
                AuditEventType.OPERATION_FAILED,
                AuditOutcome.FAILURE,
                AuditActorType.USER,
                7L,
                "lucas",
                AuditEntityType.ENDPOINT,
                "POST /api/v1/transactions/withdraw",
                "INSUFFICIENT_BALANCE",
                IP,
                409,
                correlationId
        );
    }

    private AuditEvent saved() {
        ArgumentCaptor<AuditEvent> captor =
                ArgumentCaptor.forClass(AuditEvent.class);

        verify(auditEventRepository).save(captor.capture());

        return captor.getValue();
    }

    @Test
    @DisplayName("Should persist every field of the record with the service clock")
    void shouldPersistAllFields() {
        UUID correlationId = UUID.randomUUID();

        auditService.recordDetached(record(correlationId));

        AuditEvent event = saved();

        assertEquals(AuditEventType.OPERATION_FAILED, event.getEventType());
        assertEquals(AuditOutcome.FAILURE, event.getOutcome());
        assertEquals(AuditActorType.USER, event.getActorType());
        assertEquals(7L, event.getActorUserId());
        assertEquals("lucas", event.getActorUsername());
        assertEquals(AuditEntityType.ENDPOINT, event.getEntityType());
        assertEquals(
                "POST /api/v1/transactions/withdraw",
                event.getEntityId()
        );
        assertEquals("INSUFFICIENT_BALANCE", event.getReason());
        assertEquals(IP, event.getIpAddress());
        assertEquals(409, event.getHttpStatus());
        assertEquals(correlationId, event.getCorrelationId());
        assertEquals(NOW, event.getCreatedAt());
    }

    @Test
    @DisplayName("Should not read the actor from the security context")
    void shouldNotUseCurrentUser() {
        auditService.recordDetached(record(null));

        saved();

        verifyNoInteractions(currentUserService);
    }

    @Test
    @DisplayName("Should prefer the correlation id of the record over the MDC")
    void shouldPreferRecordCorrelationId() {
        UUID fromRecord = UUID.randomUUID();

        MDC.put(
                CorrelationIdFilter.CORRELATION_ID_MDC_KEY,
                UUID.randomUUID().toString()
        );

        auditService.recordDetached(record(fromRecord));

        assertEquals(fromRecord, saved().getCorrelationId());
    }

    @Test
    @DisplayName("Should fall back to the correlation id in the MDC")
    void shouldFallBackToMdc() {
        UUID fromMdc = UUID.randomUUID();

        MDC.put(
                CorrelationIdFilter.CORRELATION_ID_MDC_KEY,
                fromMdc.toString()
        );

        auditService.recordDetached(record(null));

        assertEquals(fromMdc, saved().getCorrelationId());
    }

    @Test
    @DisplayName("Should store no correlation id when there is none")
    void shouldStoreNoCorrelationId() {
        auditService.recordDetached(record(null));

        assertNull(saved().getCorrelationId());
    }
}
