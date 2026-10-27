package com.lcsalvess.bankingsystem.entity.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro imutável de uma operação auditada. O banco rejeita UPDATE, DELETE e
 * TRUNCATE nesta tabela, e por isso nenhuma coluna é atualizável.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private AuditEventType eventType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AuditOutcome outcome;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private AuditActorType actorType;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_username", updatable = false, length = 20)
    private String actorUsername;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 30)
    private AuditEntityType entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false, updatable = false, length = 64)
    private String entityId;

    @Column(name = "correlation_id", updatable = false)
    private UUID correlationId;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(
            AuditEventType eventType,
            AuditOutcome outcome,
            AuditActorType actorType,
            Long actorUserId,
            String actorUsername,
            AuditEntityType entityType,
            String entityId,
            UUID correlationId,
            Instant createdAt
    ) {
        this.eventType = eventType;
        this.outcome = outcome;
        this.actorType = actorType;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.entityType = entityType;
        this.entityId = entityId;
        this.correlationId = correlationId;
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "The audit event creation timestamp is required."
        ).truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public AuditActorType getActorType() {
        return actorType;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
