package com.lcsalvess.bankingsystem.unit.service.audit.recording;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventRepository;
import com.lcsalvess.bankingsystem.service.audit.recording.AuditService;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Instant NOW = Instant.parse("2026-10-21T13:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZONE);

    private static final String CORRELATION_ID =
            "2f9f7c1e-3b8a-4d55-9a53-0c8f1e6b7a10";

    private static final User USER = UserTestFactory.aUser()
            .withId(7L)
            .build();

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

    @Nested
    @DisplayName("recordSuccess(AuditEventType, AuditEntityType, String)")
    class RecordSuccess {

        @Test
        @DisplayName("Should record the authenticated user as the actor")
        void shouldRecordAuthenticatedUserAsActor() {
            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(USER));

            auditService.recordSuccess(
                    AuditEventType.USER_CREATED,
                    AuditEntityType.USER,
                    "42"
            );

            AuditEvent saved = savedEvent();

            assertEquals(AuditEventType.USER_CREATED, saved.getEventType());
            assertEquals(AuditOutcome.SUCCESS, saved.getOutcome());
            assertEquals(AuditActorType.USER, saved.getActorType());
            assertEquals(7L, saved.getActorUserId());
            assertEquals(USER.getUsername(), saved.getActorUsername());
            assertEquals(AuditEntityType.USER, saved.getEntityType());
            assertEquals("42", saved.getEntityId());
            assertEquals(NOW, saved.getCreatedAt());
        }

        @Test
        @DisplayName("Should store the reason when one is provided")
        void shouldStoreReasonWhenProvided() {
            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(USER));

            auditService.recordSuccess(
                    AuditEventType.USER_ROLE_CHANGED,
                    AuditEntityType.USER,
                    "42",
                    "EMPLOYEE_TO_AUDITOR"
            );

            AuditEvent saved = savedEvent();

            assertEquals(AuditOutcome.SUCCESS, saved.getOutcome());
            assertEquals("EMPLOYEE_TO_AUDITOR", saved.getReason());
            assertNull(saved.getIpAddress());
            assertNull(saved.getHttpStatus());
        }

        @Test
        @DisplayName("Should leave the reason empty when none is provided")
        void shouldLeaveReasonEmptyWhenNoneIsProvided() {
            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.of(USER));

            auditService.recordSuccess(
                    AuditEventType.USER_CREATED,
                    AuditEntityType.USER,
                    "42"
            );

            assertNull(savedEvent().getReason());
        }

        @Test
        @DisplayName("Should record an anonymous actor when nobody is authenticated")
        void shouldRecordAnonymousActorWhenNobodyIsAuthenticated() {
            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.empty());

            auditService.recordSuccess(
                    AuditEventType.DEPOSIT,
                    AuditEntityType.TRANSACTION,
                    "abc"
            );

            AuditEvent saved = savedEvent();

            assertEquals(AuditActorType.ANONYMOUS, saved.getActorType());
            assertNull(saved.getActorUserId());
            assertNull(saved.getActorUsername());
        }

        @Test
        @DisplayName("Should store the correlation id from the MDC")
        void shouldStoreCorrelationIdFromMdc() {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, CORRELATION_ID);
            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.empty());

            auditService.recordSuccess(
                    AuditEventType.DEPOSIT,
                    AuditEntityType.TRANSACTION,
                    "abc"
            );

            assertEquals(UUID.fromString(CORRELATION_ID),
                    savedEvent().getCorrelationId());
        }

        @Test
        @DisplayName("Should store no correlation id when the MDC has none")
        void shouldStoreNoCorrelationIdWhenMdcHasNone() {
            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.empty());

            auditService.recordSuccess(
                    AuditEventType.DEPOSIT,
                    AuditEntityType.TRANSACTION,
                    "abc"
            );

            assertNull(savedEvent().getCorrelationId());
        }

        @Test
        @DisplayName("Should store no correlation id when the MDC value is not a UUID")
        void shouldStoreNoCorrelationIdWhenMdcValueIsNotUuid() {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "not-a-uuid");
            when(currentUserService.findCurrentUser())
                    .thenReturn(Optional.empty());

            auditService.recordSuccess(
                    AuditEventType.DEPOSIT,
                    AuditEntityType.TRANSACTION,
                    "abc"
            );

            assertNull(savedEvent().getCorrelationId());
        }
    }

    @Nested
    @DisplayName("recordSystemSuccess(AuditEventType, AuditEntityType, String)")
    class RecordSystemSuccess {

        @Test
        @DisplayName("Should record SYSTEM as the actor without consulting the current user")
        void shouldRecordSystemActor() {
            auditService.recordSystemSuccess(
                    AuditEventType.YIELD,
                    AuditEntityType.TRANSACTION,
                    "5b1f6c0e-8a3d-4c2e-9f47-3d2a1b0c9e88"
            );

            AuditEvent saved = savedSystemEvent();

            assertEquals(AuditEventType.YIELD, saved.getEventType());
            assertEquals(AuditOutcome.SUCCESS, saved.getOutcome());
            assertEquals(AuditActorType.SYSTEM, saved.getActorType());
            assertNull(saved.getActorUserId());
            assertNull(saved.getActorUsername());
            assertEquals(AuditEntityType.TRANSACTION, saved.getEntityType());
            assertEquals(
                    "5b1f6c0e-8a3d-4c2e-9f47-3d2a1b0c9e88",
                    saved.getEntityId()
            );
            assertEquals(NOW, saved.getCreatedAt());
        }

        @Test
        @DisplayName("Should store the correlation ID of the current run")
        void shouldStoreCorrelationId() {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, CORRELATION_ID);

            auditService.recordSystemSuccess(
                    AuditEventType.YIELD,
                    AuditEntityType.TRANSACTION,
                    "5b1f6c0e-8a3d-4c2e-9f47-3d2a1b0c9e88"
            );

            assertEquals(
                    UUID.fromString(CORRELATION_ID),
                    savedSystemEvent().getCorrelationId()
            );
        }
    }

    private AuditEvent savedEvent() {
        ArgumentCaptor<AuditEvent> captor =
                ArgumentCaptor.forClass(AuditEvent.class);

        verify(currentUserService).findCurrentUser();
        verify(auditEventRepository).save(captor.capture());
        verifyNoMoreInteractions(auditEventRepository, currentUserService);

        return captor.getValue();
    }

    private AuditEvent savedSystemEvent() {
        ArgumentCaptor<AuditEvent> captor =
                ArgumentCaptor.forClass(AuditEvent.class);

        verify(auditEventRepository).save(captor.capture());
        verifyNoInteractions(currentUserService);
        verifyNoMoreInteractions(auditEventRepository);

        return captor.getValue();
    }
}
