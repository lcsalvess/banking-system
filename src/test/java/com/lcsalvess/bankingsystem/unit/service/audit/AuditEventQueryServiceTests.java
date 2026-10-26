package com.lcsalvess.bankingsystem.unit.service.audit;

import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDTO;
import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.exception.validation.daterange.InvalidDateRangeException;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventQueryRepository;
import com.lcsalvess.bankingsystem.repository.specification.AuditEventSpecifications;
import com.lcsalvess.bankingsystem.service.audit.AuditEventQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEventQueryService tests")
class AuditEventQueryServiceTests {

    private static final ZoneId ZONE =
            ZoneId.of("America/Sao_Paulo");

    private static final LocalDate TODAY =
            LocalDate.of(2026, 10, 26);

    private static final Clock CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ZONE).toInstant(),
            ZONE
    );

    @Mock
    private AuditEventQueryRepository auditEventQueryRepository;

    private AuditEventQueryService auditEventQueryService;

    @BeforeEach
    void setUp() {
        auditEventQueryService = new AuditEventQueryService(
                auditEventQueryRepository,
                CLOCK
        );
    }

    @Nested
    @DisplayName("findAll")
    class FindAllTests {

        @Test
        @DisplayName("Should return mapped DTOs and add an ID tie-breaker")
        void shouldReturnMappedDtosAndAddIdTieBreaker() {
            Filters filters = Filters.empty();

            Pageable requestedPageable = PageRequest.of(
                    0,
                    20,
                    Sort.by(Sort.Order.desc("createdAt"))
            );

            Pageable expectedPageable = PageRequest.of(
                    0,
                    20,
                    Sort.by(
                            Sort.Order.desc("createdAt"),
                            Sort.Order.asc("id")
                    )
            );

            AuditEvent event = createEvent(1L);
            AuditEventResponseDTO expected =
                    AuditEventResponseDTO.fromEntity(event);

            Specification<AuditEvent> specification = specification();

            Page<AuditEvent> repositoryPage = new PageImpl<>(
                    List.of(event),
                    expectedPageable,
                    1
            );

            try (MockedStatic<AuditEventSpecifications> specifications =
                         mockStatic(AuditEventSpecifications.class)) {

                stubSpecification(specifications, filters, specification);

                when(auditEventQueryRepository.findAll(
                        specification,
                        expectedPageable
                )).thenReturn(repositoryPage);

                Page<AuditEventResponseDTO> result =
                        executeQuery(filters, requestedPageable);

                assertEquals(List.of(expected), result.getContent());
                assertEquals(1, result.getTotalElements());
                assertEquals(1, result.getTotalPages());
                assertEquals(expectedPageable, result.getPageable());

                verifySpecification(specifications, filters);
                specifications.verifyNoMoreInteractions();
            }

            verify(auditEventQueryRepository).findAll(
                    specification,
                    expectedPageable
            );
            verifyNoMoreInteractions(auditEventQueryRepository);
        }

        @Test
        @DisplayName("Should forward all filters and preserve an existing ID sort")
        void shouldForwardAllFiltersAndPreserveExistingIdSort() {
            Filters filters = new Filters(
                    LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 25),
                    AuditEventType.TRANSFER,
                    AuditOutcome.SUCCESS,
                    42L,
                    "lucas",
                    AuditEntityType.ACCOUNT,
                    "12345"
            );

            Pageable requestedPageable = PageRequest.of(
                    2,
                    10,
                    Sort.by(
                            Sort.Order.asc("eventType"),
                            Sort.Order.desc("id")
                    )
            );

            Specification<AuditEvent> specification = specification();

            AuditEvent event = createEvent(2L);

            Page<AuditEvent> repositoryPage = new PageImpl<>(
                    List.of(event),
                    requestedPageable,
                    21
            );

            try (MockedStatic<AuditEventSpecifications> specifications =
                         mockStatic(AuditEventSpecifications.class)) {

                stubSpecification(specifications, filters, specification);

                when(auditEventQueryRepository.findAll(
                        specification,
                        requestedPageable
                )).thenReturn(repositoryPage);

                Page<AuditEventResponseDTO> result =
                        executeQuery(filters, requestedPageable);

                assertEquals(
                        List.of(AuditEventResponseDTO.fromEntity(event)),
                        result.getContent()
                );
                assertEquals(21, result.getTotalElements());
                assertEquals(3, result.getTotalPages());
                assertEquals(requestedPageable, result.getPageable());

                verifySpecification(specifications, filters);
                specifications.verifyNoMoreInteractions();
            }

            verify(auditEventQueryRepository).findAll(
                    specification,
                    requestedPageable
            );
            verifyNoMoreInteractions(auditEventQueryRepository);
        }
    }

    @Nested
    @DisplayName("Validação do período")
    class DateRangeValidationTests {

        @Test
        @DisplayName("Should reject a start date before the minimum supported date")
        void shouldRejectStartDateBeforeMinimum() {
            Filters filters = new Filters(
                    LocalDate.of(2019, 12, 31),
                    TODAY,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            assertInvalidDateRange(filters);
        }

        @Test
        @DisplayName("Should reject a date in the future")
        void shouldRejectFutureDate() {
            Filters filters = new Filters(
                    TODAY,
                    TODAY.plusDays(1),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            assertInvalidDateRange(filters);
        }

        @Test
        @DisplayName("Should reject a start date later than the end date")
        void shouldRejectStartDateAfterEndDate() {
            Filters filters = new Filters(
                    LocalDate.of(2026, 10, 20),
                    LocalDate.of(2026, 10, 10),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            assertInvalidDateRange(filters);
        }

        @Test
        @DisplayName("Should reject a range wider than the maximum allowed")
        void shouldRejectRangeWiderThanMaximum() {
            Filters filters = new Filters(
                    TODAY.minusDays(AuditEventQueryService.MAX_RANGE_DAYS),
                    TODAY,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            assertInvalidDateRange(filters);
        }

        private void assertInvalidDateRange(Filters filters) {
            assertThrows(
                    InvalidDateRangeException.class,
                    () -> executeQuery(
                            filters,
                            PageRequest.of(0, 20)
                    )
            );

            verifyNoInteractions(auditEventQueryRepository);
        }
    }

    private Page<AuditEventResponseDTO> executeQuery(
            Filters filters,
            Pageable pageable
    ) {
        return auditEventQueryService.findAll(
                filters.startDate(),
                filters.endDate(),
                filters.eventType(),
                filters.outcome(),
                filters.actorUserId(),
                filters.actorUsername(),
                filters.entityType(),
                filters.entityId(),
                pageable
        );
    }

    private static void stubSpecification(
            MockedStatic<AuditEventSpecifications> specifications,
            Filters filters,
            Specification<AuditEvent> specification
    ) {
        specifications.when(() -> AuditEventSpecifications.byFilters(
                filters.startDate(),
                filters.endDate(),
                filters.eventType(),
                filters.outcome(),
                filters.actorUserId(),
                filters.actorUsername(),
                filters.entityType(),
                filters.entityId()
        )).thenReturn(specification);
    }

    private static void verifySpecification(
            MockedStatic<AuditEventSpecifications> specifications,
            Filters filters
    ) {
        specifications.verify(() -> AuditEventSpecifications.byFilters(
                filters.startDate(),
                filters.endDate(),
                filters.eventType(),
                filters.outcome(),
                filters.actorUserId(),
                filters.actorUsername(),
                filters.entityType(),
                filters.entityId()
        ));
    }

    private static Specification<AuditEvent> specification() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.conjunction();
    }

    private static AuditEvent createEvent(Long id) {
        AuditEvent event = new AuditEvent(
                AuditEventType.USER_CREATED,
                AuditOutcome.SUCCESS,
                AuditActorType.USER,
                7L,
                "admin",
                AuditEntityType.USER,
                "42",
                UUID.randomUUID(),
                LocalDateTime.of(2026, 10, 25, 10, 30)
        );

        ReflectionTestUtils.setField(event, "id", id);

        return event;
    }

    private record Filters(
            LocalDate startDate,
            LocalDate endDate,
            AuditEventType eventType,
            AuditOutcome outcome,
            Long actorUserId,
            String actorUsername,
            AuditEntityType entityType,
            String entityId
    ) {
        private static Filters empty() {
            return new Filters(
                    null, null, null, null,
                    null, null, null, null
            );
        }
    }
}