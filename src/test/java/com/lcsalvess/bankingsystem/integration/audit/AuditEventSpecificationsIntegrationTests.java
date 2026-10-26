package com.lcsalvess.bankingsystem.integration.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventQueryRepository;
import com.lcsalvess.bankingsystem.repository.specification.AuditEventSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
@DisplayName("AuditEventSpecifications integration tests")
class AuditEventSpecificationsIntegrationTests {

    private static final LocalDate START_DATE =
            LocalDate.of(2025, 3, 10);

    private static final LocalDate END_DATE =
            LocalDate.of(2025, 3, 11);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AuditEventQueryRepository auditEventQueryRepository;

    @Nested
    @DisplayName("Filtros de data")
    class DateFiltersTests {

        @Test
        @DisplayName("Should include both boundary dates and exclude adjacent dates")
        void shouldIncludeBothBoundaryDates() {
            Long actorUserId = uniqueActorUserId();

            String beforeRangeId = uniqueEntityId();
            String startBoundaryId = uniqueEntityId();
            String endBoundaryId = uniqueEntityId();
            String afterRangeId = uniqueEntityId();

            persistEvents(
                    event(
                            AuditEventType.DEPOSIT,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "date-filter",
                            AuditEntityType.TRANSACTION,
                            beforeRangeId,
                            LocalDateTime.of(
                                    2025, 3, 9, 23, 59, 59, 999_999_000
                            )
                    ),
                    event(
                            AuditEventType.DEPOSIT,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "date-filter",
                            AuditEntityType.TRANSACTION,
                            startBoundaryId,
                            START_DATE.atStartOfDay()
                    ),
                    event(
                            AuditEventType.DEPOSIT,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "date-filter",
                            AuditEntityType.TRANSACTION,
                            endBoundaryId,
                            LocalDateTime.of(
                                    2025, 3, 11, 23, 59, 59, 999_999_000
                            )
                    ),
                    event(
                            AuditEventType.DEPOSIT,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "date-filter",
                            AuditEntityType.TRANSACTION,
                            afterRangeId,
                            END_DATE.plusDays(1).atStartOfDay()
                    )
            );

            Page<AuditEvent> result = findEvents(
                    START_DATE,
                    END_DATE,
                    null,
                    null,
                    actorUserId,
                    null,
                    null,
                    null
            );

            assertEquals(
                    Set.of(startBoundaryId, endBoundaryId),
                    entityIds(result)
            );
        }
    }

    @Nested
    @DisplayName("Filtros de evento e entidade")
    class EventAndEntityFiltersTests {

        @Test
        @DisplayName("Should combine event type, outcome, actor and entity filters")
        void shouldCombineFiltersUsingAnd() {
            Long actorUserId = uniqueActorUserId();
            String entityId = uniqueEntityId();

            persistEvents(
                    event(
                            AuditEventType.TRANSFER,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "filter-user",
                            AuditEntityType.ACCOUNT,
                            entityId,
                            LocalDateTime.of(2025, 3, 10, 10, 0)
                    ),
                    event(
                            AuditEventType.DEPOSIT,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "filter-user",
                            AuditEntityType.ACCOUNT,
                            entityId,
                            LocalDateTime.of(2025, 3, 10, 10, 1)
                    ),
                    event(
                            AuditEventType.TRANSFER,
                            AuditOutcome.DENIED,
                            actorUserId,
                            "filter-user",
                            AuditEntityType.ACCOUNT,
                            entityId,
                            LocalDateTime.of(2025, 3, 10, 10, 2)
                    ),
                    event(
                            AuditEventType.TRANSFER,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "filter-user",
                            AuditEntityType.CLIENT,
                            entityId,
                            LocalDateTime.of(2025, 3, 10, 10, 3)
                    )
            );

            Page<AuditEvent> result = findEvents(
                    null,
                    null,
                    AuditEventType.TRANSFER,
                    AuditOutcome.SUCCESS,
                    actorUserId,
                    null,
                    AuditEntityType.ACCOUNT,
                    entityId
            );

            assertEquals(1, result.getTotalElements());
            assertEquals(AuditEventType.TRANSFER,
                    result.getContent().getFirst().getEventType());
            assertEquals(AuditOutcome.SUCCESS,
                    result.getContent().getFirst().getOutcome());
            assertEquals(AuditEntityType.ACCOUNT,
                    result.getContent().getFirst().getEntityType());
        }

        @Test
        @DisplayName("Should match an exact entity ID after trimming whitespace")
        void shouldMatchExactEntityIdAfterTrimming() {
            Long actorUserId = uniqueActorUserId();
            String expectedEntityId = uniqueEntityId();
            String otherEntityId = uniqueEntityId();

            persistEvents(
                    event(
                            AuditEventType.USER_CREATED,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "entity-filter",
                            AuditEntityType.USER,
                            expectedEntityId,
                            LocalDateTime.of(2025, 3, 10, 10, 0)
                    ),
                    event(
                            AuditEventType.USER_CREATED,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "entity-filter",
                            AuditEntityType.USER,
                            otherEntityId,
                            LocalDateTime.of(2025, 3, 10, 10, 1)
                    )
            );

            Page<AuditEvent> result = findEvents(
                    null,
                    null,
                    null,
                    null,
                    actorUserId,
                    null,
                    null,
                    "  " + expectedEntityId + "  "
            );

            assertEquals(Set.of(expectedEntityId), entityIds(result));
        }
    }

    @Nested
    @DisplayName("Filtro por nome do ator")
    class ActorUsernameFilterTests {

        @Test
        @DisplayName("Should match the beginning of a username case-insensitively")
        void shouldMatchUsernamePrefixIgnoringCase() {
            Long actorUserId = uniqueActorUserId();
            String matchingEntityId = uniqueEntityId();
            String otherEntityId = uniqueEntityId();

            persistEvents(
                    event(
                            AuditEventType.USER_CREATED,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "lucas-auditor",
                            AuditEntityType.USER,
                            matchingEntityId,
                            LocalDateTime.of(2025, 3, 10, 10, 0)
                    ),
                    event(
                            AuditEventType.USER_CREATED,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "employee-user",
                            AuditEntityType.USER,
                            otherEntityId,
                            LocalDateTime.of(2025, 3, 10, 10, 1)
                    )
            );

            Page<AuditEvent> result = findEvents(
                    null,
                    null,
                    null,
                    null,
                    actorUserId,
                    "  LUCAS  ",
                    null,
                    null
            );

            assertEquals(Set.of(matchingEntityId), entityIds(result));
        }

        @Test
        @DisplayName("Should not match a term found only in the middle of a username")
        void shouldNotMatchUsernameSubstring() {
            Long actorUserId = uniqueActorUserId();
            String entityId = uniqueEntityId();

            persistEvents(
                    event(
                            AuditEventType.USER_CREATED,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "lucas-auditor",
                            AuditEntityType.USER,
                            entityId,
                            LocalDateTime.of(2025, 3, 10, 10, 0)
                    )
            );

            Page<AuditEvent> result = findEvents(
                    null,
                    null,
                    null,
                    null,
                    actorUserId,
                    "AUDITOR",
                    null,
                    null
            );

            assertEquals(Set.of(), entityIds(result));
        }

        @Test
        @DisplayName("Should treat SQL LIKE wildcard characters as literal characters")
        void shouldEscapeLikeWildcardCharacters() {
            Long actorUserId = uniqueActorUserId();
            String matchingEntityId = uniqueEntityId();
            String otherEntityId = uniqueEntityId();

            persistEvents(
                    event(
                            AuditEventType.USER_CREATED,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "audit_%_target",
                            AuditEntityType.USER,
                            matchingEntityId,
                            LocalDateTime.of(2025, 3, 10, 10, 0)
                    ),
                    event(
                            AuditEventType.USER_CREATED,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "auditXYZtarget",
                            AuditEntityType.USER,
                            otherEntityId,
                            LocalDateTime.of(2025, 3, 10, 10, 1)
                    )
            );

            Page<AuditEvent> result = findEvents(
                    null,
                    null,
                    null,
                    null,
                    actorUserId,
                    "AUDIT_%",
                    null,
                    null
            );

            assertEquals(Set.of(matchingEntityId), entityIds(result));
        }

        @Test
        @DisplayName("Should ignore blank optional text filters")
        void shouldIgnoreBlankOptionalTextFilters() {
            Long actorUserId = uniqueActorUserId();
            String firstEntityId = uniqueEntityId();
            String secondEntityId = uniqueEntityId();

            persistEvents(
                    event(
                            AuditEventType.USER_CREATED,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "first-user",
                            AuditEntityType.USER,
                            firstEntityId,
                            LocalDateTime.of(2025, 3, 10, 10, 0)
                    ),
                    event(
                            AuditEventType.USER_DEACTIVATED,
                            AuditOutcome.SUCCESS,
                            actorUserId,
                            "second-user",
                            AuditEntityType.USER,
                            secondEntityId,
                            LocalDateTime.of(2025, 3, 10, 10, 1)
                    )
            );

            Page<AuditEvent> result = findEvents(
                    null,
                    null,
                    null,
                    null,
                    actorUserId,
                    "   ",
                    null,
                    "   "
            );

            assertEquals(
                    Set.of(firstEntityId, secondEntityId),
                    entityIds(result)
            );
        }
    }

    private Page<AuditEvent> findEvents(
            LocalDate startDate,
            LocalDate endDate,
            AuditEventType eventType,
            AuditOutcome outcome,
            Long actorUserId,
            String actorUsername,
            AuditEntityType entityType,
            String entityId
    ) {
        return auditEventQueryRepository.findAll(
                AuditEventSpecifications.byFilters(
                        startDate,
                        endDate,
                        eventType,
                        outcome,
                        actorUserId,
                        actorUsername,
                        entityType,
                        entityId
                ),
                Pageable.unpaged()
        );
    }

    private void persistEvents(AuditEvent... events) {
        Stream.of(events).forEach(entityManager::persist);
        entityManager.flush();
        entityManager.clear();
    }

    private static AuditEvent event(
            AuditEventType eventType,
            AuditOutcome outcome,
            Long actorUserId,
            String actorUsername,
            AuditEntityType entityType,
            String entityId,
            LocalDateTime createdAt
    ) {
        return new AuditEvent(
                eventType,
                outcome,
                AuditActorType.USER,
                actorUserId,
                actorUsername,
                entityType,
                entityId,
                UUID.randomUUID(),
                createdAt
        );
    }

    private static Set<String> entityIds(Page<AuditEvent> page) {
        return page.getContent()
                .stream()
                .map(AuditEvent::getEntityId)
                .collect(Collectors.toSet());
    }

    private static Long uniqueActorUserId() {
        return ThreadLocalRandom.current()
                .nextLong(1_000_000_000L, Long.MAX_VALUE);
    }

    private static String uniqueEntityId() {
        return "audit-spec-" + UUID.randomUUID();
    }
}
