package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDTO;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.service.audit.AuditEventQueryService;
import com.lcsalvess.bankingsystem.util.pagination.SortWhitelist;
import com.lcsalvess.bankingsystem.util.pagination.SortableBy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

@Tag(
        name = "Audit Events",
        description = "Read-only access to the audit trail."
)
@PreAuthorize("hasRole('AUDITOR')")
@Validated
@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditEventController {

    private static final String SORTABLE =
            "id,eventType,outcome,actorType,actorUserId,actorUsername,"
                    + "entityType,entityId,correlationId,createdAt";

    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of(SORTABLE.split(","));

    private final AuditEventQueryService auditEventQueryService;

    public AuditEventController(
            AuditEventQueryService auditEventQueryService
    ) {
        this.auditEventQueryService = auditEventQueryService;
    }

    @Operation(
            summary = "Search audit events",
            description = """
                    Retrieves a paginated, read-only view of audit events.
                    Optional filters can narrow the search by date range,
                    event type, outcome, actor, or affected entity.
                    Only users with the AUDITOR role may access this endpoint.
                    """
    )
    @SortableBy(
            value = SORTABLE,
            defaultSort = "createdAt,DESC;id,DESC"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Audit events retrieved successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid filters, date range, pagination, or sort parameters."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is missing or invalid."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The authenticated user is not an auditor."
            )
    })
    @GetMapping
    public Page<AuditEventResponseDTO> findAll(
            @Parameter(description = "Start date, inclusive (yyyy-MM-dd)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @Parameter(description = "End date, inclusive (yyyy-MM-dd)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @Parameter(description = "Filter by event type")
            @RequestParam(required = false)
            AuditEventType eventType,

            @Parameter(description = "Filter by recorded outcome")
            @RequestParam(required = false)
            AuditOutcome outcome,

            @Parameter(description = "Filter by the numeric ID of the actor")
            @RequestParam(required = false)
            @Positive
            Long actorUserId,

            @Parameter(description = "Search by part of the actor's username")
            @RequestParam(required = false)
            @Size(max = 20)
            String actorUsername,

            @Parameter(description = "Filter by affected entity type")
            @RequestParam(required = false)
            AuditEntityType entityType,

            @Parameter(description = "Filter by the exact affected entity ID")
            @RequestParam(required = false)
            @Size(max = 64)
            String entityId,

            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

        return auditEventQueryService.findAll(
                startDate,
                endDate,
                eventType,
                outcome,
                actorUserId,
                actorUsername,
                entityType,
                entityId,
                pageable
        );
    }
}