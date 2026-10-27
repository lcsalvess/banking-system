package com.lcsalvess.bankingsystem.unit.controller;

import com.lcsalvess.bankingsystem.config.pagination.PaginationConfig;
import com.lcsalvess.bankingsystem.controller.AuditEventController;
import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDTO;
import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.service.audit.AuditEventQueryService;
import com.lcsalvess.bankingsystem.unit.config.WebMvcTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditEventController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcTestSecurityConfig.class,
        PaginationConfig.class
})
class AuditEventControllerTests {

    private static final String URL = "/api/v1/audit-events";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditEventQueryService auditEventQueryService;

    @Nested
    @DisplayName("Consulta de eventos")
    class FindAllTests {

        @Test
        @DisplayName("Should return a page using the default pagination and sorting")
        void shouldReturnPaginatedAuditEvents() throws Exception {
            UUID correlationId = UUID.randomUUID();

            Pageable expectedPageable = PageRequest.of(
                    0,
                    20,
                    defaultSort()
            );

            stubFindAll(new PageImpl<>(
                    List.of(getEvent(correlationId)),
                    expectedPageable,
                    1
            ));

            performGet()
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    ))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].eventType")
                            .value("USER_CREATED"))
                    .andExpect(jsonPath("$.content[0].outcome")
                            .value("SUCCESS"))
                    .andExpect(jsonPath("$.content[0].actorType")
                            .value("USER"))
                    .andExpect(jsonPath("$.content[0].actorUserId").value(7))
                    .andExpect(jsonPath("$.content[0].actorUsername")
                            .value("admin"))
                    .andExpect(jsonPath("$.content[0].entityType")
                            .value("USER"))
                    .andExpect(jsonPath("$.content[0].entityId").value("42"))
                    .andExpect(jsonPath("$.content[0].correlationId")
                            .value(correlationId.toString()))
                    .andExpect(jsonPath("$.page.totalElements").value(1))
                    .andExpect(jsonPath("$.page.totalPages").value(1));

            Pageable actualPageable = verifyFindAll(
                    null, null, null, null, null, null, null, null
            );

            assertEquals(0, actualPageable.getPageNumber());
            assertEquals(20, actualPageable.getPageSize());
            assertEquals(defaultSort(), actualPageable.getSort());
        }

        @Test
        @DisplayName("Should forward filters, pagination and sorting to the service")
        void shouldForwardFiltersAndPagination() throws Exception {
            LocalDate startDate = LocalDate.of(2026, 10, 1);
            LocalDate endDate = LocalDate.of(2026, 10, 25);

            Pageable requestedPageable = PageRequest.of(
                    2,
                    10,
                    Sort.by(Sort.Order.asc("eventType"))
            );

            stubFindAll(new PageImpl<>(
                    List.of(getEvent(UUID.randomUUID())),
                    requestedPageable,
                    21
            ));

            performGet(
                    "startDate", "2026-10-01",
                    "endDate", "2026-10-25",
                    "eventType", "TRANSFER",
                    "outcome", "SUCCESS",
                    "actorUserId", "42",
                    "actorUsername", "lucas",
                    "entityType", "ACCOUNT",
                    "entityId", "12345",
                    "page", "2",
                    "size", "10",
                    "sort", "eventType,asc"
            )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page.totalElements").value(21))
                    .andExpect(jsonPath("$.page.totalPages").value(3));

            Pageable actualPageable = verifyFindAll(
                    startDate,
                    endDate,
                    AuditEventType.TRANSFER,
                    AuditOutcome.SUCCESS,
                    42L,
                    "lucas",
                    AuditEntityType.ACCOUNT,
                    "12345"
            );

            assertEquals(2, actualPageable.getPageNumber());
            assertEquals(10, actualPageable.getPageSize());
            assertEquals(
                    Sort.by(Sort.Order.asc("eventType")),
                    actualPageable.getSort()
            );
        }

        private AuditEventResponseDTO getEvent(UUID correlationId) {
            Instant createdAt =
                    Instant.parse("2026-10-26T13:15:30Z");

            return new AuditEventResponseDTO(
                    1L,
                    AuditEventType.USER_CREATED,
                    AuditOutcome.SUCCESS,
                    AuditActorType.USER,
                    7L,
                    "admin",
                    AuditEntityType.USER,
                    "42",
                    correlationId,
                    createdAt
            );
        }
    }

    @Nested
    @DisplayName("Validação dos parâmetros")
    class ParameterValidationTests {

        @Test
        @DisplayName("Should reject a sort property outside the whitelist")
        void shouldRejectUnsupportedSortProperty() throws Exception {
            expectBadRequest("sort", "passwordHash,asc");
        }

        @Test
        @DisplayName("Should reject a date with an invalid format")
        void shouldRejectInvalidDateParameter() throws Exception {
            expectBadRequest("startDate", "2026-13-01");
        }

        @Test
        @DisplayName("Should reject a non-positive actor user ID")
        void shouldRejectNonPositiveActorUserId() throws Exception {
            expectBadRequest("actorUserId", "0");
        }

        @Test
        @DisplayName("Should reject an actor username exceeding the maximum length")
        void shouldRejectOversizedActorUsername() throws Exception {
            expectBadRequest("actorUsername", "a".repeat(21));
        }

        @Test
        @DisplayName("Should reject an entity ID exceeding the maximum length")
        void shouldRejectOversizedEntityId() throws Exception {
            expectBadRequest("entityId", "a".repeat(65));
        }
    }

    private ResultActions performGet(String... parameters) throws Exception {
        if (parameters.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Parameters must be provided as name/value pairs."
            );
        }

        MockHttpServletRequestBuilder request = get(URL);

        for (int i = 0; i < parameters.length; i += 2) {
            request.param(parameters[i], parameters[i + 1]);
        }

        return mockMvc.perform(request);
    }

    private void expectBadRequest(String parameter, String value)
            throws Exception {
        performGet(parameter, value)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(auditEventQueryService);
    }

    private void stubFindAll(Page<AuditEventResponseDTO> result) {
        when(auditEventQueryService.findAll(
                nullable(LocalDate.class),
                nullable(LocalDate.class),
                nullable(AuditEventType.class),
                nullable(AuditOutcome.class),
                nullable(Long.class),
                nullable(String.class),
                nullable(AuditEntityType.class),
                nullable(String.class),
                any(Pageable.class)
        )).thenReturn(result);
    }

    private Pageable verifyFindAll(
            LocalDate startDate,
            LocalDate endDate,
            AuditEventType eventType,
            AuditOutcome outcome,
            Long actorUserId,
            String actorUsername,
            AuditEntityType entityType,
            String entityId
    ) {
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(auditEventQueryService).findAll(
                eqOrNull(startDate),
                eqOrNull(endDate),
                eqOrNull(eventType),
                eqOrNull(outcome),
                eqOrNull(actorUserId),
                eqOrNull(actorUsername),
                eqOrNull(entityType),
                eqOrNull(entityId),
                pageableCaptor.capture()
        );

        verifyNoMoreInteractions(auditEventQueryService);

        return pageableCaptor.getValue();
    }

    private static <T> T eqOrNull(T value) {
        return value == null ? isNull() : eq(value);
    }

    private static Sort defaultSort() {
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
    }
}