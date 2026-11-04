package com.lcsalvess.bankingsystem.dto.request.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventQueryFilter;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record AuditEventQueryRequestDTO(

        @Parameter(description = "Start date, inclusive (yyyy-MM-dd)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @Parameter(description = "End date, inclusive (yyyy-MM-dd)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,

        @Parameter(description = "Filter by specific event type")
        AuditEventType eventType,

        @Parameter(description = "Filter by success, failure, or denial")
        AuditOutcome outcome,

        @Parameter(description = "Filter by the ID of the user who performed the action")
        @Positive
        Long actorUserId,

        @Parameter(description = "Search by the beginning of the actor's username (case-insensitive)")
        @Size(max = 20)
        String actorUsername,

        @Parameter(description = "Filter by the type of the affected entity")
        AuditEntityType entityType,

        @Parameter(description = "Filter by the exact identifier of the affected entity")
        @Size(max = 64)
        String entityId

) {

    public AuditEventQueryFilter toFilter() {
        return new AuditEventQueryFilter(
                startDate,
                endDate,
                eventType,
                outcome,
                actorUserId,
                actorUsername,
                entityType,
                entityId
        );
    }
}
