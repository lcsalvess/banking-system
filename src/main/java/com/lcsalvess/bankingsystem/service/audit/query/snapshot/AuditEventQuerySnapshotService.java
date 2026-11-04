package com.lcsalvess.bankingsystem.service.audit.query.snapshot;

import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDTO;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.snapshot.AuditEventQuerySnapshot;
import com.lcsalvess.bankingsystem.exception.audit.InvalidAuditEventQuerySnapshotException;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventQueryRepository;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventQuerySnapshotItemRepository;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventQuerySnapshotRepository;
import com.lcsalvess.bankingsystem.repository.specification.AuditEventSpecifications;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventPageResult;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventQueryFilter;
import com.lcsalvess.bankingsystem.service.audit.policy.AuditEventQueryPolicy;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.util.pagination.PageableTieBreaker;
import com.lcsalvess.bankingsystem.validation.daterange.QueryDateRangeValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditEventQuerySnapshotService {

    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(15);

    private static final int MAX_TOTAL_ELEMENTS =
            AuditEventQuerySnapshot.MAX_TOTAL_ELEMENTS;

    private static final int MAX_PAGE_SIZE =
            AuditEventQueryPolicy.MAX_PAGE_SIZE;

    private static final int MAX_ACTIVE_SNAPSHOTS_PER_ACTOR =
            AuditEventQueryPolicy.MAX_ACTIVE_SNAPSHOTS_PER_ACTOR;

    private static final String INVALID_SNAPSHOT_MESSAGE =
            "The audit query snapshot is invalid, expired, or does not match the request.";

    private static final String INVALID_PAGE_SIZE_MESSAGE =
            "The audit event page size must be between 1 and "
                    + MAX_PAGE_SIZE
                    + ".";

    private static final String SNAPSHOT_LIMIT_MESSAGE =
            "The audit query exceeds the maximum of "
                    + MAX_TOTAL_ELEMENTS
                    + " events. Narrow the filters or date range.";

    private final AuditEventQuerySnapshotRepository snapshotRepository;
    private final AuditEventQuerySnapshotItemRepository itemRepository;
    private final AuditEventQueryRepository auditEventQueryRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public AuditEventQuerySnapshotService(
            AuditEventQuerySnapshotRepository snapshotRepository,
            AuditEventQuerySnapshotItemRepository itemRepository,
            AuditEventQueryRepository auditEventQueryRepository,
            CurrentUserService currentUserService,
            Clock clock
    ) {
        this.snapshotRepository = snapshotRepository;
        this.itemRepository = itemRepository;
        this.auditEventQueryRepository = auditEventQueryRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    /**
     * Creates a new immutable result-set snapshot for the authenticated auditor.
     *
     * <p>The transaction uses REQUIRES_NEW and REPEATABLE_READ so the event IDs
     * are materialized from a consistent database view and persisted together
     * with the snapshot metadata.
     *
     * <p>The auditor keeps at most {@code MAX_ACTIVE_SNAPSHOTS_PER_ACTOR}
     * unexpired snapshots; the oldest ones are discarded to make room.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.REPEATABLE_READ
    )
    public AuditEventPageResult createSnapshot(
            AuditEventQueryFilter filter,
            Pageable requestedPageable
    ) {
        validateDateRange(filter);

        Pageable pageable = validateAndApplyTieBreaker(requestedPageable);

        Long currentActorId = currentActorId();

        Specification<AuditEvent> specification = buildSpecification(filter);

        List<Long> eventIds = auditEventQueryRepository.findMatchingEventIds(
                specification,
                pageable.getSort(),
                MAX_TOTAL_ELEMENTS + 1
        );

        if (eventIds.size() > MAX_TOTAL_ELEMENTS) {
            throw new InvalidAuditEventQuerySnapshotException(
                    SNAPSHOT_LIMIT_MESSAGE
            );
        }

        int totalElements = eventIds.size();

        byte[] filterHash =
                AuditEventQuerySnapshotFingerprint.filters(filter);

        byte[] sortHash =
                AuditEventQuerySnapshotFingerprint.sort(pageable.getSort());

        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = createdAt.plus(SNAPSHOT_TTL);

        snapshotRepository.deleteOldestActiveBeyond(
                currentActorId,
                createdAt,
                MAX_ACTIVE_SNAPSHOTS_PER_ACTOR - 1
        );

        UUID snapshotId = UUID.randomUUID();

        AuditEventQuerySnapshot snapshot =
                AuditEventQuerySnapshot.create(
                        snapshotId,
                        currentActorId,
                        filterHash,
                        sortHash,
                        totalElements,
                        createdAt,
                        expiresAt
                );

        snapshotRepository.save(snapshot);
        itemRepository.saveAll(snapshotId, eventIds);

        return loadPage(snapshot, pageable);
    }

    /**
     * Retrieves a page from an existing snapshot.
     *
     * <p>The snapshot is only accessible to its owner and only while active.
     * The request must preserve the original filters and sorting. The page
     * number and page size are free: positions are absolute, so only the
     * result set and its order are fixed by the snapshot.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.REPEATABLE_READ,
            readOnly = true
    )
    public AuditEventPageResult findPage(
            AuditEventQueryFilter filter,
            Pageable requestedPageable,
            String snapshotToken
    ) {
        validateDateRange(filter);

        Pageable pageable = validateAndApplyTieBreaker(requestedPageable);

        UUID snapshotId = parseSnapshotToken(snapshotToken);

        Long currentActorId = currentActorId();

        Instant now = clock.instant();

        AuditEventQuerySnapshot snapshot =
                snapshotRepository.findActiveByIdAndActor(
                        snapshotId,
                        currentActorId,
                        now
                ).orElseThrow(this::invalidSnapshot);

        byte[] requestedFilterHash =
                AuditEventQuerySnapshotFingerprint.filters(filter);

        byte[] requestedSortHash =
                AuditEventQuerySnapshotFingerprint.sort(pageable.getSort());

        boolean filtersMatch = MessageDigest.isEqual(
                snapshot.getFilterHash(),
                requestedFilterHash
        );

        boolean sortMatches = MessageDigest.isEqual(
                snapshot.getSortHash(),
                requestedSortHash
        );

        if (!filtersMatch || !sortMatches) {
            throw invalidSnapshot();
        }

        return loadPage(snapshot, pageable);
    }

    private AuditEventPageResult loadPage(
            AuditEventQuerySnapshot snapshot,
            Pageable pageable
    ) {
        long offset = pageable.getOffset();

        List<Long> eventIds;

        if (offset >= snapshot.getTotalElements()) {
            eventIds = List.of();
        } else {
            eventIds = itemRepository.findEventIds(
                    snapshot.getSnapshotId(),
                    Math.toIntExact(offset),
                    pageable.getPageSize()
            );
        }

        List<AuditEventResponseDTO> content =
                loadEventsInSnapshotOrder(eventIds);

        Page<AuditEventResponseDTO> page = new PageImpl<>(
                content,
                pageable,
                snapshot.getTotalElements()
        );

        return new AuditEventPageResult(
                page,
                snapshot.getSnapshotId().toString()
        );
    }

    private List<AuditEventResponseDTO> loadEventsInSnapshotOrder(
            List<Long> eventIds
    ) {
        if (eventIds.isEmpty()) {
            return List.of();
        }

        List<AuditEvent> events =
                auditEventQueryRepository.findByIdIn(eventIds);

        Map<Long, AuditEvent> eventsById = new HashMap<>();

        for (AuditEvent event : events) {
            eventsById.put(event.getId(), event);
        }

        return eventIds.stream()
                .map(id -> {
                    AuditEvent event = eventsById.get(id);

                    if (event == null) {
                        throw new IllegalStateException(
                                "A materialized audit event could not be found."
                        );
                    }

                    return AuditEventResponseDTO.fromEntity(event);
                })
                .toList();
    }

    private Specification<AuditEvent> buildSpecification(
            AuditEventQueryFilter filter
    ) {
        return AuditEventSpecifications.byFilters(
                filter.startDate(),
                filter.endDate(),
                filter.eventType(),
                filter.outcome(),
                filter.actorUserId(),
                filter.actorUsername(),
                filter.entityType(),
                filter.entityId()
        );
    }

    private Long currentActorId() {
        Long actorId = currentUserService.getCurrentUser().getId();

        if (actorId == null || actorId <= 0) {
            throw new IllegalStateException(
                    "The authenticated auditor must have a persisted user ID."
            );
        }

        return actorId;
    }

    private Pageable validateAndApplyTieBreaker(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            throw invalidPageSize();
        }

        if (pageable.getPageSize() < 1
                || pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw invalidPageSize();
        }

        return PageableTieBreaker.withIdTieBreaker(pageable);
    }

    private void validateDateRange(AuditEventQueryFilter filter) {
        QueryDateRangeValidator.validate(
                filter.startDate(),
                filter.endDate(),
                clock,
                AuditEventQueryPolicy.MAX_RANGE_DAYS
        );
    }

    private UUID parseSnapshotToken(String snapshotToken) {
        if (snapshotToken == null || snapshotToken.isBlank()) {
            throw invalidSnapshot();
        }

        try {
            UUID snapshotId = UUID.fromString(snapshotToken);

            if (!snapshotId.toString().equalsIgnoreCase(snapshotToken)) {
                throw invalidSnapshot();
            }

            return snapshotId;
        } catch (IllegalArgumentException exception) {
            throw invalidSnapshot();
        }
    }

    private InvalidAuditEventQuerySnapshotException invalidSnapshot() {
        return new InvalidAuditEventQuerySnapshotException(
                INVALID_SNAPSHOT_MESSAGE
        );
    }

    private InvalidAuditEventQuerySnapshotException invalidPageSize() {
        return new InvalidAuditEventQuerySnapshotException(
                INVALID_PAGE_SIZE_MESSAGE
        );
    }
}
