package com.lcsalvess.bankingsystem.service.audit.contract;

import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import java.time.LocalDate;

/**
 * Represents the filtering criteria used to query audit events.
 *
 * @param startDate inclusive start date of the query period
 * @param endDate inclusive end date of the query period
 * @param eventType optional audit event type filter
 * @param outcome optional audit outcome filter
 * @param actorUserId optional identifier of the user who performed the action
 * @param actorUsername optional username of the actor
 * @param entityType optional type of the affected entity
 * @param entityId optional identifier of the affected entity
 */
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
