package com.lcsalvess.bankingsystem.unit.service.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventRepository;
import com.lcsalvess.bankingsystem.service.audit.AuditService;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 10, 21, 10, 30);
    private static final Clock CLOCK =
            Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private static final String CORRELATION_ID =
            "2f9f7c1e-3b8a-4d55-9a53-0c8f1e6b7a10";

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
                    .thenReturn(Optional.of(user(7L, "lucas")));

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
            assertEquals("lucas", saved.getActorUsername());
            assertEquals(AuditEntityType.USER, saved.getEntityType());
            assertEquals("42", saved.getEntityId());
            assertEquals(NOW, saved.getCreatedAt());
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

    private AuditEvent savedEvent() {
        ArgumentCaptor<AuditEvent> captor =
                ArgumentCaptor.forClass(AuditEvent.class);

        verify(currentUserService).findCurrentUser();
        verify(auditEventRepository).save(captor.capture());
        verifyNoMoreInteractions(auditEventRepository, currentUserService);

        return captor.getValue();
    }

    private User user(Long id, String username) {
        User user = new User(
                username,
                username + "@example.com",
                "hash",
                Role.EMPLOYEE
        );

        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }
}
