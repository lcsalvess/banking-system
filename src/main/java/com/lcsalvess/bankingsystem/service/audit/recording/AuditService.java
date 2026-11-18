package com.lcsalvess.bankingsystem.service.audit.recording;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventRepository;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records audit events while preserving the transactional requirements of each operation.
 */
@Service
public class AuditService {

  private final AuditEventRepository auditEventRepository;
  private final CurrentUserService currentUserService;
  private final Clock clock;

  public AuditService(
      AuditEventRepository auditEventRepository,
      CurrentUserService currentUserService,
      Clock clock
  ) {
    this.auditEventRepository = auditEventRepository;
    this.currentUserService = currentUserService;
    this.clock = clock;
  }

  /**
   * Records a successful operation in the SAME transaction as the audited
   * change. With MANDATORY, calling this method without an active transaction
   * fails explicitly, and any failure to persist the audit event rolls back
   * the entire operation: a committed operation can never exist without
   * its corresponding audit record.
   *
   * @param eventType the type of the audited event
   * @param entityType the type of the affected entity
   * @param entityId the identifier of the affected entity
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordSuccess(
      AuditEventType eventType,
      AuditEntityType entityType,
      String entityId
  ) {
    recordSuccess(eventType, entityType, entityId, null);
  }

  /**
   * Records a successful operation in the SAME transaction as the audited
   * change, including a reason code describing what changed.
   * <p>
   * The reason must match {@code ck_audit_events_reason}: uppercase letters,
   * digits, and underscores, with a maximum length of 60 characters.
   *
   * @param eventType the type of the audited event
   * @param entityType the type of the affected entity
   * @param entityId the identifier of the affected entity
   * @param reason the reason code describing the change, or {@code null}
   *               when no additional reason is required
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordSuccess(
      AuditEventType eventType,
      AuditEntityType entityType,
      String entityId,
      String reason
  ) {
    Optional<User> actor = currentUserService.findCurrentUser();

    auditEventRepository.save(
        AuditEvent.create(
            eventType,
            AuditOutcome.SUCCESS,
            actor.isPresent()
                ? AuditActorType.USER
                : AuditActorType.ANONYMOUS,
            actor.map(User::getId).orElse(null),
            actor.map(User::getUsername).orElse(null),
            entityType,
            entityId,
            currentCorrelationId(),
            clock.instant(),
            null,
            reason,
            null
        )
    );
  }

  /**
   * Records a successful system-generated operation, such as the daily yield,
   * in the SAME transaction as the audited change.
   * <p>
   * Scheduled work has no authenticated user, so the actor is always SYSTEM.
   * Consulting the current user in this context would record the event as
   * ANONYMOUS.
   *
   * @param eventType the type of the audited event
   * @param entityType the type of the affected entity
   * @param entityId the identifier of the affected entity
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordSystemSuccess(
      AuditEventType eventType,
      AuditEntityType entityType,
      String entityId
  ) {
    auditEventRepository.save(
        AuditEvent.create(
            eventType,
            AuditOutcome.SUCCESS,
            AuditActorType.SYSTEM,
            null,
            null,
            entityType,
            entityId,
            currentCorrelationId(),
            clock.instant(),
            null,
            null,
            null
        )
    );
  }

  /**
   * Records an event independently of the operation's transaction, including
   * failures that trigger rollback, access denials, and login events.
   * <p>
   * Runs in its own transaction (REQUIRES_NEW) and should therefore only
   * be called when no transaction is active. Calling it from within an active
   * transaction would require an additional database connection from the pool.
   * Callers typically use {@link AuditTrailRecorder}, which never propagates
   * audit write failures.
   *
   * @param record the details of the detached audit event to record
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordDetached(DetachedAuditRecord record) {
    UUID correlationId = record.correlationId() != null
        ? record.correlationId()
        : currentCorrelationId();

    auditEventRepository.save(
        AuditEvent.create(
            record.eventType(),
            record.outcome(),
            record.actorType(),
            record.actorUserId(),
            record.actorUsername(),
            record.entityType(),
            record.entityId(),
            correlationId,
            clock.instant(),
            record.ipAddress(),
            record.reason(),
            record.httpStatus()
        )
    );
  }

  private UUID currentCorrelationId() {
    String correlationId =
        MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

    if (correlationId == null) {
      return null;
    }

    try {
      return UUID.fromString(correlationId);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
