package com.lcsalvess.bankingsystem.entity.audit;

import com.lcsalvess.bankingsystem.util.time.InstantPrecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of an audited operation. The database rejects UPDATE,
 * DELETE, and TRUNCATE operations on this table, so no column is updatable.
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

    @Column(name = "ip_address", updatable = false)
    private InetAddress ipAddress;

    @Column(updatable = false, length = 60)
    private String reason;

    @Column(name = "http_status", updatable = false)
    private Integer httpStatus;

    /**
     * Constructor required by JPA. It is not public to prevent application
     * code from creating instances without using the factory methods.
     */
    protected AuditEvent() {
    }

    /**
     * Creates an audit event without additional HTTP information.
     *
     * @param eventType the type of event being recorded
     * @param outcome the outcome of the audited operation
     * @param actorType the type of actor responsible for the operation
     * @param actorUserId the ID of the user responsible for the operation, if applicable
     * @param actorUsername the username of the actor, if applicable
     * @param entityType the type of entity affected by the operation
     * @param entityId the identifier of the affected entity
     * @param correlationId the correlation ID associated with the request, if available
     * @param createdAt the timestamp when the event occurred
     * @return a new audit event
     */
    public static AuditEvent create(
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
        return create(
                eventType,
                outcome,
                actorType,
                actorUserId,
                actorUsername,
                entityType,
                entityId,
                correlationId,
                createdAt,
                null,
                null,
                null
        );
    }

    /**
     * Creates an audit event with additional HTTP information.
     *
     * @param eventType the type of event being recorded
     * @param outcome the outcome of the audited operation
     * @param actorType the type of actor responsible for the operation
     * @param actorUserId the ID of the user responsible for the operation, if applicable
     * @param actorUsername the username of the actor, if applicable
     * @param entityType the type of entity affected by the operation
     * @param entityId the identifier of the affected entity
     * @param correlationId the correlation ID associated with the request, if available
     * @param createdAt the timestamp when the event occurred
     * @param ipAddress the IP address associated with the request, if available
     * @param reason the reason associated with the event, if applicable
     * @param httpStatus the HTTP response status, if applicable
     * @return a new audit event
     */
    public static AuditEvent create(
            AuditEventType eventType,
            AuditOutcome outcome,
            AuditActorType actorType,
            Long actorUserId,
            String actorUsername,
            AuditEntityType entityType,
            String entityId,
            UUID correlationId,
            Instant createdAt,
            InetAddress ipAddress,
            String reason,
            Integer httpStatus
    ) {
        AuditEventType validatedEventType = Objects.requireNonNull(
                eventType,
                "The audit event type is required."
        );

        AuditOutcome validatedOutcome = Objects.requireNonNull(
                outcome,
                "The audit event outcome is required."
        );

        AuditActorType validatedActorType = Objects.requireNonNull(
                actorType,
                "The audit event actor type is required."
        );

        AuditEntityType validatedEntityType = Objects.requireNonNull(
                entityType,
                "The audit event entity type is required."
        );

        String validatedEntityId = Objects.requireNonNull(
                entityId,
                "The audit event entity ID is required."
        );

        Instant validatedCreatedAt = validateCreatedAt(createdAt);

        return new AuditEvent(
                validatedEventType,
                validatedOutcome,
                validatedActorType,
                actorUserId,
                actorUsername,
                validatedEntityType,
                validatedEntityId,
                correlationId,
                validatedCreatedAt,
                ipAddress,
                reason,
                httpStatus
        );
    }

    /**
     * Internal constructor with no validation or operations that may throw
     * exceptions during object initialization.
     */
    private AuditEvent(
            AuditEventType eventType,
            AuditOutcome outcome,
            AuditActorType actorType,
            Long actorUserId,
            String actorUsername,
            AuditEntityType entityType,
            String entityId,
            UUID correlationId,
            Instant createdAt,
            InetAddress ipAddress,
            String reason,
            Integer httpStatus
    ) {
        this.eventType = eventType;
        this.outcome = outcome;
        this.actorType = actorType;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.entityType = entityType;
        this.entityId = entityId;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
        this.ipAddress = ipAddress;
        this.reason = reason;
        this.httpStatus = httpStatus;
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

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public String getReason() {
        return reason;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    private static Instant validateCreatedAt(Instant createdAt) {
        return InstantPrecision.truncateToMicros(
                createdAt,
                "The audit event creation timestamp is required."
        );
    }
}
