package com.lcsalvess.bankingsystem.dto.response.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEventResponseDTO(
        Long id,
        AuditEventType eventType,
        AuditOutcome outcome,
        AuditActorType actorType,
        Long actorUserId,
        String actorUsername,
        AuditEntityType entityType,
        String entityId,
        UUID correlationId,
        LocalDateTime createdAt
) {

    public static AuditEventResponseDTO fromEntity(AuditEvent event) {
        return new AuditEventResponseDTO(
                event.getId(),
                event.getEventType(),
                event.getOutcome(),
                event.getActorType(),
                event.getActorUserId(),
                event.getActorUsername(),
                event.getEntityType(),
                event.getEntityId(),
                event.getCorrelationId(),
                event.getCreatedAt()
        );
    }
}