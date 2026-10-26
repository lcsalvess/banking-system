package com.lcsalvess.bankingsystem.repository.specification;

import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.util.normalization.OptionalTextNormalizer;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

public final class AuditEventSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private AuditEventSpecifications() {
    }

    public static Specification<AuditEvent> byFilters(
            LocalDate startDate,
            LocalDate endDate,
            AuditEventType eventType,
            AuditOutcome outcome,
            Long actorUserId,
            String actorUsername,
            AuditEntityType entityType,
            String entityId
    ) {
        String normalizedUsername =
                OptionalTextNormalizer.normalize(actorUsername);

        String usernamePattern = normalizedUsername == null
                ? null
                : escapeLike(
                normalizedUsername.toLowerCase(Locale.ROOT)
        )
                + "%";

        String normalizedEntityId =
                OptionalTextNormalizer.normalize(entityId);

        LocalDateTime startDateTime = startDate == null
                ? null
                : startDate.atStartOfDay();

        LocalDateTime endDateTimeExclusive = endDate == null
                ? null
                : endDate.plusDays(1).atStartOfDay();

        return (root, query, cb) -> cb.and(
                startDateTime == null
                        ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        startDateTime
                ),

                endDateTimeExclusive == null
                        ? cb.conjunction()
                        : cb.lessThan(
                        root.get("createdAt"),
                        endDateTimeExclusive
                ),

                eventType == null
                        ? cb.conjunction()
                        : cb.equal(root.get("eventType"), eventType),

                outcome == null
                        ? cb.conjunction()
                        : cb.equal(root.get("outcome"), outcome),

                actorUserId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("actorUserId"), actorUserId),

                usernamePattern == null
                        ? cb.conjunction()
                        : cb.like(
                        cb.lower(root.get("actorUsername")),
                        usernamePattern,
                        LIKE_ESCAPE
                ),

                entityType == null
                        ? cb.conjunction()
                        : cb.equal(root.get("entityType"), entityType),

                normalizedEntityId == null
                        ? cb.conjunction()
                        : cb.equal(
                        root.get("entityId"),
                        normalizedEntityId
                )
        );
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}