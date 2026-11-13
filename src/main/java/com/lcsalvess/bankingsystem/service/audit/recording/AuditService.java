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
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

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
     * Same as {@link #recordSuccess(AuditEventType, AuditEntityType, String)},
     * with a reason code that describes what changed (for example, the previous
     * and new role). The reason must match {@code ck_audit_events_reason}:
     * upper case letters, digits and underscores, up to 60 characters.
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
     * Records a successful system-generated operation (for example, the daily
     * yield) in the SAME transaction as the audited change. There is no
     * authenticated user in scheduled work, so the actor is always SYSTEM;
     * consulting the current user here would record the event as ANONYMOUS.
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
     * Records an event that must not depend on the operation's transaction:
     * failures (which trigger rollback), access denials, and login events.
     *
     * <p>Runs in its own transaction (REQUIRES_NEW) and should therefore only
     * be called when no transaction is active. Calling it from within an active
     * transaction would require an additional database connection from the pool.
     * Callers typically use {@link AuditTrailRecorder}, which never propagates
     * audit write failures.
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
