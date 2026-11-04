package com.lcsalvess.bankingsystem.service.audit.contract;

import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;

import java.time.LocalDate;

public record AuditEventQueryFilter(
        LocalDate startDate,
        LocalDate endDate,
        AuditEventType eventType,
        AuditOutcome outcome,
        Long actorUserId,
        String actorUsername,
        AuditEntityType entityType,
        String entityId
) {
}
