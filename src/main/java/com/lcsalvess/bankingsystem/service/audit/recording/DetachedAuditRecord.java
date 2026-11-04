package com.lcsalvess.bankingsystem.service.audit.recording;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;

import java.net.InetAddress;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Data carrier for an audit event recorded outside the operation's transaction.
 *
 * <p>The compact constructor validates required fields and normalizes
 * externally supplied values (username, entity identifier, and reason)
 * to comply with the audit table constraints. Malformed data must not
 * cause a best-effort audit write to fail.
 *
 * <p>IP addresses are represented as {@link InetAddress} and must be
 * parsed before this record is created.
 */
public record DetachedAuditRecord(
        AuditEventType eventType,
        AuditOutcome outcome,
        AuditActorType actorType,
        Long actorUserId,
        String actorUsername,
        AuditEntityType entityType,
        String entityId,
        String reason,
        InetAddress ipAddress,
        Integer httpStatus,
        UUID correlationId
) {

    static final int MAX_USERNAME_LENGTH = 20;
    static final int MAX_ENTITY_ID_LENGTH = 64;
    static final String UNKNOWN_ENTITY_ID = "UNKNOWN";
    static final String UNSPECIFIED_REASON = "UNSPECIFIED";

    private static final Pattern REASON_PATTERN =
            Pattern.compile("^[A-Z][A-Z0-9_]{0,59}$");

    public DetachedAuditRecord {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(entityType, "entityType");

        actorUsername = truncate(actorUsername, MAX_USERNAME_LENGTH);

        entityId = truncate(entityId, MAX_ENTITY_ID_LENGTH);
        if (entityId == null || entityId.isBlank()) {
            entityId = UNKNOWN_ENTITY_ID;
        }

        if (reason == null || !REASON_PATTERN.matcher(reason).matches()) {
            reason = outcome == AuditOutcome.SUCCESS ? null : UNSPECIFIED_REASON;
        }

        if (httpStatus != null && (httpStatus < 100 || httpStatus > 599)) {
            httpStatus = null;
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}
