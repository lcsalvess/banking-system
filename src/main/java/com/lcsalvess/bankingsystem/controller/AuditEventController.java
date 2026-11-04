package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.request.audit.AuditEventQueryRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDTO;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventPageResult;
import com.lcsalvess.bankingsystem.service.audit.query.AuditEventQueryService;
import com.lcsalvess.bankingsystem.util.pagination.SortWhitelist;
import com.lcsalvess.bankingsystem.util.pagination.SortableBy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;


@Tag(
        name = "Audit Events",
        description = """
                Read-only audit trail queries.
                Access is restricted to users with the AUDITOR role.
                """
)
@PreAuthorize("hasRole('AUDITOR')")
@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditEventController {

    private static final String SNAPSHOT_HEADER = "X-Audit-Snapshot";

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
            summary = "Find audit events",
            description = """
                    Retrieves a paginated, read-only view of audit events.
                    Optional filters can narrow the search by date range,
                    event type, outcome, actor, or affected entity.
                    
                    A request without the X-Audit-Snapshot header creates a
                    query snapshot and returns its identifier in the
                    X-Audit-Snapshot response header. Send that header with
                    the same filters and sort in subsequent requests to keep
                    the original result set and ordering. The page number and
                    page size may change. A snapshot expires after 15 minutes
                    and belongs to the auditor who created it.
                    
                    A query matching more than 10,000 events is rejected:
                    narrow the filters or the date range. Each auditor keeps
                    at most 5 active snapshots; creating another one discards
                    the oldest.
                    
                    Only users with the AUDITOR role may access this endpoint.
                    """
    )
    @SortableBy(
            value = SORTABLE,
            defaultSort = "createdAt,DESC;id,DESC"
    )
    @SecurityRequirement(
            name = "bearerAuth"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Audit events retrieved successfully.",
                    headers = @Header(
                            name = SNAPSHOT_HEADER,
                            description = "Snapshot identifier to reuse when navigating audit event pages.",
                            schema = @Schema(type = "string", format = "uuid")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid filters, date range, pagination, sort parameters, or audit snapshot, or a query matching more than 10,000 events."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The authenticated user is not an auditor."
            )
    })
    @GetMapping
    public ResponseEntity<Page<AuditEventResponseDTO>> findAll(
            @Valid
            @ModelAttribute
            @ParameterObject
            AuditEventQueryRequestDTO request,

            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC
            )
            Pageable pageable,

            @Parameter(
                    description = "Snapshot identifier returned by the previous page request. "
                            + "Reuse it to preserve the original result set.",
                    schema = @Schema(type = "string", format = "uuid")
            )
            @RequestHeader(
                    name = SNAPSHOT_HEADER,
                    required = false
            )
            String snapshotToken
    ) {
        SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

        AuditEventPageResult result = auditEventQueryService.findAll(
                request.toFilter(),
                pageable,
                snapshotToken
        );

        return ResponseEntity.ok()
                .header(SNAPSHOT_HEADER, result.snapshotToken())
                .body(result.page());
    }
}
