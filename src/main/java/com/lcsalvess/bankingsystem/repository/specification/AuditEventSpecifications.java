package com.lcsalvess.bankingsystem.repository.specification;

import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.util.normalization.OptionalTextNormalizer;
import com.lcsalvess.bankingsystem.util.time.AppTimeZone;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds database specifications for filtering audit events.
 */
public final class AuditEventSpecifications {

  private static final char LIKE_ESCAPE = '\\';

  private AuditEventSpecifications() {
  }

  /**
   * Creates a specification using the supplied audit event filters.
   * <p>
   * Date boundaries are interpreted in the application time zone. The start date
   * is inclusive, while the end date is exclusive of the following day.
   * <p>
   * Actor usernames are normalized and matched by prefix, with SQL LIKE
   * wildcard characters escaped. Entity identifiers are normalized and
   * compared using exact matching.
   *
   * @param startDate the inclusive start date of the search period, or {@code null}
   * @param endDate the inclusive end date of the search period, or {@code null}
   * @param eventType the event type to match, or {@code null}
   * @param outcome the event outcome to match, or {@code null}
   * @param actorUserId the identifier of the event actor, or {@code null}
   * @param actorUsername the actor username prefix to match, or {@code null}
   * @param entityType the type of the affected entity, or {@code null}
   * @param entityId the normalized identifier of the affected entity, or {@code null}
   * @return a specification combining all provided filters
   */
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

    Instant startDateTime = startDate == null
        ? null
        : startDate.atStartOfDay(AppTimeZone.ZONE).toInstant();

    Instant endDateTimeExclusive = endDate == null
        ? null
        : endDate.plusDays(1)
        .atStartOfDay(AppTimeZone.ZONE)
        .toInstant();

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
