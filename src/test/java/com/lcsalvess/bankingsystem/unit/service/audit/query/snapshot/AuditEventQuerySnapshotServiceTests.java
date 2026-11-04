package com.lcsalvess.bankingsystem.unit.service.audit.query.snapshot;

import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDTO;
import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.audit.snapshot.AuditEventQuerySnapshot;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.audit.InvalidAuditEventQuerySnapshotException;
import com.lcsalvess.bankingsystem.exception.validation.daterange.InvalidDateRangeException;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventQueryRepository;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventQuerySnapshotItemRepository;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventQuerySnapshotRepository;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventQueryFilter;
import com.lcsalvess.bankingsystem.service.audit.policy.AuditEventQueryPolicy;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotFingerprint;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotService;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEventQuerySnapshotService tests")
class AuditEventQuerySnapshotServiceTests {

    private static final Long ACTOR_ID = 42L;

    private static final UUID SNAPSHOT_ID =
            UUID.fromString("5d6f0cb8-bc9a-4a4e-8d43-1f9e5c7d2a10");

    private static final Instant NOW =
            Instant.parse("2026-11-03T20:00:00.123456Z");

    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    private static final int PAGE_SIZE = 2;

    private static final int MAX_RESULTS =
            AuditEventQuerySnapshot.MAX_TOTAL_ELEMENTS;

    @Mock
    private AuditEventQuerySnapshotRepository snapshotRepository;

    @Mock
    private AuditEventQuerySnapshotItemRepository itemRepository;

    @Mock
    private AuditEventQueryRepository auditEventQueryRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private User currentUser;

    private AuditEventQuerySnapshotService service;

    @BeforeEach
    void setUp() {
        service = new AuditEventQuerySnapshotService(
                snapshotRepository,
                itemRepository,
                auditEventQueryRepository,
                currentUserService,
                CLOCK
        );
    }

    @Nested
    @DisplayName("createSnapshot")
    class CreateSnapshotTests {

        @Test
        @DisplayName("Should create a snapshot and preserve the materialized event order")
        void shouldCreateSnapshotAndPreserveEventOrder() {
            stubCurrentUser();

            AuditEventQueryFilter filter = emptyFilter();

            AuditEvent firstEvent = createEvent(101L);
            AuditEvent secondEvent = createEvent(102L);

            Pageable requestedPageable = PageRequest.of(
                    0,
                    PAGE_SIZE,
                    Sort.by(Sort.Order.desc("createdAt"))
            );

            Sort effectiveSort = Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.asc("id")
            );

            when(auditEventQueryRepository.findMatchingEventIds(
                    any(),
                    eq(effectiveSort),
                    eq(MAX_RESULTS + 1)
            )).thenReturn(List.of(101L, 102L));

            when(itemRepository.findEventIds(
                    any(UUID.class),
                    eq(0),
                    eq(PAGE_SIZE)
            )).thenReturn(List.of(101L, 102L));

            /*
             * Deliberately return the entities in the opposite order.
             * The service must reconstruct the materialized order.
             */
            when(auditEventQueryRepository.findByIdIn(
                    eq(List.of(101L, 102L))
            )).thenReturn(List.of(secondEvent, firstEvent));

            var result = service.createSnapshot(
                    filter,
                    requestedPageable
            );

            assertEquals(
                    List.of(
                            AuditEventResponseDTO.fromEntity(firstEvent),
                            AuditEventResponseDTO.fromEntity(secondEvent)
                    ),
                    result.page().getContent()
            );

            assertEquals(2, result.page().getTotalElements());
            assertEquals(1, result.page().getTotalPages());
            assertEquals(effectiveSort, result.page().getSort());

            UUID generatedSnapshotId =
                    UUID.fromString(result.snapshotToken());

            ArgumentCaptor<AuditEventQuerySnapshot> snapshotCaptor =
                    ArgumentCaptor.forClass(AuditEventQuerySnapshot.class);

            verify(snapshotRepository).save(snapshotCaptor.capture());

            AuditEventQuerySnapshot savedSnapshot =
                    snapshotCaptor.getValue();

            assertEquals(generatedSnapshotId, savedSnapshot.getSnapshotId());
            assertEquals(ACTOR_ID, savedSnapshot.getActorUserId());
            assertEquals(2, savedSnapshot.getTotalElements());
            assertEquals(NOW, savedSnapshot.getCreatedAt());

            assertEquals(
                    NOW.plusSeconds(15 * 60),
                    savedSnapshot.getExpiresAt()
            );

            verify(itemRepository).saveAll(
                    generatedSnapshotId,
                    List.of(101L, 102L)
            );

            verify(auditEventQueryRepository).findMatchingEventIds(
                    any(),
                    eq(effectiveSort),
                    eq(MAX_RESULTS + 1)
            );

            verify(itemRepository).findEventIds(
                    generatedSnapshotId,
                    0,
                    PAGE_SIZE
            );

            verify(auditEventQueryRepository).findByIdIn(
                    List.of(101L, 102L)
            );
        }

        @Test
        @DisplayName("Should create a snapshot when the result is empty")
        void shouldCreateSnapshotWithEmptyResult() {
            stubCurrentUser();

            Pageable pageable = PageRequest.of(
                    0,
                    PAGE_SIZE,
                    DEFAULT_SORT
            );

            when(auditEventQueryRepository.findMatchingEventIds(
                    any(),
                    eq(DEFAULT_SORT),
                    eq(MAX_RESULTS + 1)
            )).thenReturn(List.of());

            var result = service.createSnapshot(
                    emptyFilter(),
                    pageable
            );

            assertTrue(result.page().isEmpty());
            assertEquals(0, result.page().getTotalElements());
            assertEquals(0, result.page().getTotalPages());

            ArgumentCaptor<AuditEventQuerySnapshot> snapshotCaptor =
                    ArgumentCaptor.forClass(AuditEventQuerySnapshot.class);

            verify(snapshotRepository).save(snapshotCaptor.capture());

            assertEquals(
                    0,
                    snapshotCaptor.getValue().getTotalElements()
            );

            verify(itemRepository).saveAll(
                    UUID.fromString(result.snapshotToken()),
                    List.of()
            );

            verify(itemRepository, never()).findEventIds(
                    any(UUID.class),
                    anyInt(),
                    anyInt()
            );

            verify(auditEventQueryRepository, never()).findByIdIn(any());
        }

        @Test
        @DisplayName("Should reject a result exceeding the maximum snapshot size")
        void shouldRejectResultExceedingMaximumSize() {
            stubCurrentUser();

            when(auditEventQueryRepository.findMatchingEventIds(
                    any(),
                    eq(DEFAULT_SORT),
                    eq(MAX_RESULTS + 1)
            )).thenReturn(
                    LongStream.rangeClosed(1, MAX_RESULTS + 1L)
                            .boxed()
                            .toList()
            );

            assertThrows(
                    InvalidAuditEventQuerySnapshotException.class,
                    () -> service.createSnapshot(
                            emptyFilter(),
                            PageRequest.of(0, PAGE_SIZE, DEFAULT_SORT)
                    )
            );

            verifyNoInteractions(snapshotRepository, itemRepository);
            verify(currentUserService).getCurrentUser();
            verify(currentUser).getId();
        }

        @Test
        @DisplayName("Should reject a page size exceeding the configured maximum")
        void shouldRejectPageSizeAboveMaximum() {
            assertThrows(
                    InvalidAuditEventQuerySnapshotException.class,
                    () -> service.createSnapshot(
                            emptyFilter(),
                            PageRequest.of(
                                    0,
                                    AuditEventQueryPolicy.MAX_PAGE_SIZE + 1
                            )
                    )
            );

            verifyNoInteractions(
                    snapshotRepository,
                    itemRepository,
                    auditEventQueryRepository,
                    currentUserService
            );
        }

        @Test
        @DisplayName("Should reject snapshot creation when the current user has no persisted ID")
        void shouldRejectCurrentUserWithoutPersistedId() {
            stubCurrentUser(null);

            assertThrows(
                    IllegalStateException.class,
                    () -> service.createSnapshot(
                            emptyFilter(),
                            PageRequest.of(0, PAGE_SIZE, DEFAULT_SORT)
                    )
            );

            verifyNoInteractions(
                    auditEventQueryRepository,
                    snapshotRepository,
                    itemRepository
            );

            verify(currentUserService).getCurrentUser();
            verify(currentUser).getId();
        }

        @Test
        @DisplayName("Should reject snapshot creation when the current user ID is not positive")
        void shouldRejectNonPositiveCurrentUserId() {
            stubCurrentUser(0L);

            assertThrows(
                    IllegalStateException.class,
                    () -> service.createSnapshot(
                            emptyFilter(),
                            PageRequest.of(0, PAGE_SIZE, DEFAULT_SORT)
                    )
            );

            verifyNoInteractions(
                    auditEventQueryRepository,
                    snapshotRepository,
                    itemRepository
            );

            verify(currentUserService).getCurrentUser();
            verify(currentUser).getId();
        }

        @Test
        @DisplayName("Should discard the oldest active snapshots of the actor before saving")
        void shouldDiscardOldestSnapshotsBeforeSaving() {
            stubCurrentUser();

            when(auditEventQueryRepository.findMatchingEventIds(
                    any(),
                    eq(DEFAULT_SORT),
                    eq(MAX_RESULTS + 1)
            )).thenReturn(List.of());

            service.createSnapshot(
                    emptyFilter(),
                    PageRequest.of(0, PAGE_SIZE, DEFAULT_SORT)
            );

            var order = inOrder(snapshotRepository);

            order.verify(snapshotRepository).deleteOldestActiveBeyond(
                    ACTOR_ID,
                    NOW,
                    AuditEventQueryPolicy.MAX_ACTIVE_SNAPSHOTS_PER_ACTOR - 1
            );
            order.verify(snapshotRepository)
                    .save(any(AuditEventQuerySnapshot.class));
        }

        @Test
        @DisplayName("Should not discard anything when the result is rejected as too large")
        void shouldNotDiscardSnapshotsWhenResultIsTooLarge() {
            stubCurrentUser();

            when(auditEventQueryRepository.findMatchingEventIds(
                    any(),
                    eq(DEFAULT_SORT),
                    eq(MAX_RESULTS + 1)
            )).thenReturn(
                    LongStream.rangeClosed(1, MAX_RESULTS + 1L)
                            .boxed()
                            .toList()
            );

            assertThrows(
                    InvalidAuditEventQuerySnapshotException.class,
                    () -> service.createSnapshot(
                            emptyFilter(),
                            PageRequest.of(0, PAGE_SIZE, DEFAULT_SORT)
                    )
            );

            verifyNoInteractions(snapshotRepository, itemRepository);
        }

        @Test
        @DisplayName("Should reject a start date before the minimum supported date")
        void shouldRejectStartDateBeforeMinimum() {
            assertInvalidDateRange(LocalDate.of(2019, 12, 31), TODAY);
        }

        @Test
        @DisplayName("Should reject a date in the future")
        void shouldRejectFutureDate() {
            assertInvalidDateRange(TODAY, TODAY.plusDays(1));
        }

        @Test
        @DisplayName("Should reject a start date later than the end date")
        void shouldRejectStartDateAfterEndDate() {
            assertInvalidDateRange(
                    LocalDate.of(2026, 10, 20),
                    LocalDate.of(2026, 10, 10)
            );
        }

        @Test
        @DisplayName("Should reject a range wider than the maximum allowed")
        void shouldRejectRangeWiderThanMaximum() {
            assertInvalidDateRange(
                    TODAY.minusDays(AuditEventQueryPolicy.MAX_RANGE_DAYS),
                    TODAY
            );
        }

        @Test
        @DisplayName("Should reject an unpaged request")
        void shouldRejectUnpagedRequest() {
            assertThrows(
                    InvalidAuditEventQuerySnapshotException.class,
                    () -> service.createSnapshot(
                            emptyFilter(),
                            Pageable.unpaged()
                    )
            );

            verifyNoInteractions(
                    snapshotRepository,
                    itemRepository,
                    auditEventQueryRepository,
                    currentUserService
            );
        }

        private void assertInvalidDateRange(
                LocalDate startDate,
                LocalDate endDate
        ) {
            assertThrows(
                    InvalidDateRangeException.class,
                    () -> service.createSnapshot(
                            dates(startDate, endDate),
                            PageRequest.of(0, PAGE_SIZE, DEFAULT_SORT)
                    )
            );

            verifyNoInteractions(
                    snapshotRepository,
                    itemRepository,
                    auditEventQueryRepository,
                    currentUserService
            );
        }
    }

    @Nested
    @DisplayName("findPage")
    class FindPageTests {

        @Test
        @DisplayName("Should retrieve a page using the stored snapshot positions")
        void shouldRetrievePageFromSnapshot() {
            stubCurrentUser();

            AuditEvent event = createEvent(103L);

            stubActiveSnapshot(
                    emptyFilterHash(),
                    defaultSortHash()
            );

            Pageable pageable = PageRequest.of(
                    1,
                    PAGE_SIZE,
                    DEFAULT_SORT
            );

            when(itemRepository.findEventIds(
                    SNAPSHOT_ID,
                    2,
                    PAGE_SIZE
            )).thenReturn(List.of(103L));

            when(auditEventQueryRepository.findByIdIn(
                    eq(List.of(103L))
            )).thenReturn(List.of(event));

            var result = service.findPage(
                    emptyFilter(),
                    pageable,
                    SNAPSHOT_ID.toString()
            );

            assertEquals(
                    List.of(AuditEventResponseDTO.fromEntity(event)),
                    result.page().getContent()
            );

            assertEquals(1, result.page().getNumber());
            assertEquals(PAGE_SIZE, result.page().getSize());
            assertEquals(3, result.page().getTotalElements());
            assertEquals(SNAPSHOT_ID.toString(), result.snapshotToken());

            verify(snapshotRepository).findActiveByIdAndActor(
                    SNAPSHOT_ID,
                    ACTOR_ID,
                    NOW
            );

            verify(itemRepository).findEventIds(
                    SNAPSHOT_ID,
                    2,
                    PAGE_SIZE
            );

            verify(auditEventQueryRepository).findByIdIn(
                    List.of(103L)
            );
        }

        @Test
        @DisplayName("Should preserve an empty page beyond the snapshot result")
        void shouldReturnEmptyPageBeyondSnapshotResult() {
            stubCurrentUser();

            stubActiveSnapshot(
                    emptyFilterHash(),
                    defaultSortHash()
            );

            Pageable pageable = PageRequest.of(
                    2,
                    PAGE_SIZE,
                    DEFAULT_SORT
            );

            var result = service.findPage(
                    emptyFilter(),
                    pageable,
                    SNAPSHOT_ID.toString()
            );

            assertTrue(result.page().isEmpty());
            assertEquals(2, result.page().getNumber());
            assertEquals(3, result.page().getTotalElements());
            assertEquals(SNAPSHOT_ID.toString(), result.snapshotToken());

            verify(itemRepository, never()).findEventIds(
                    any(UUID.class),
                    anyInt(),
                    anyInt()
            );

            verify(auditEventQueryRepository, never()).findByIdIn(any());
        }

        @Test
        @DisplayName("Should reject a malformed snapshot token")
        void shouldRejectMalformedSnapshotToken() {
            assertThrows(
                    InvalidAuditEventQuerySnapshotException.class,
                    () -> service.findPage(
                            emptyFilter(),
                            PageRequest.of(0, PAGE_SIZE, DEFAULT_SORT),
                            "not-a-uuid"
                    )
            );

            verifyNoInteractions(
                    currentUserService,
                    snapshotRepository,
                    itemRepository,
                    auditEventQueryRepository
            );
        }

        @Test
        @DisplayName("Should reject a snapshot that is missing, expired, or owned by another user")
        void shouldRejectUnavailableSnapshot() {
            stubCurrentUser();

            when(snapshotRepository.findActiveByIdAndActor(
                    SNAPSHOT_ID,
                    ACTOR_ID,
                    NOW
            )).thenReturn(Optional.empty());

            InvalidAuditEventQuerySnapshotException exception =
                    assertThrows(
                            InvalidAuditEventQuerySnapshotException.class,
                            () -> service.findPage(
                                    emptyFilter(),
                                    PageRequest.of(0, PAGE_SIZE, DEFAULT_SORT),
                                    SNAPSHOT_ID.toString()
                            )
                    );

            assertTrue(
                    exception.getMessage() != null
                            && !exception.getMessage().isBlank()
            );

            verify(snapshotRepository).findActiveByIdAndActor(
                    SNAPSHOT_ID,
                    ACTOR_ID,
                    NOW
            );

            verifyNoInteractions(itemRepository, auditEventQueryRepository);
        }

        @Test
        @DisplayName("Should reject a snapshot when the filters have changed")
        void shouldRejectChangedFilters() {
            stubCurrentUser();

            stubActiveSnapshotForValidation(
                    emptyFilterHash(),
                    defaultSortHash()
            );

            AuditEventQueryFilter changedFilter =
                    new AuditEventQueryFilter(
                            null,
                            null,
                            null,
                            null,
                            null,
                            "lucas",
                            null,
                            null
                    );

            assertThrows(
                    InvalidAuditEventQuerySnapshotException.class,
                    () -> service.findPage(
                            changedFilter,
                            PageRequest.of(0, PAGE_SIZE, DEFAULT_SORT),
                            SNAPSHOT_ID.toString()
                    )
            );

            verifyNoInteractions(itemRepository, auditEventQueryRepository);
        }

        @Test
        @DisplayName("Should reject a snapshot when the sort has changed")
        void shouldRejectChangedSort() {
            stubCurrentUser();

            stubActiveSnapshotForValidation(
                    emptyFilterHash(),
                    defaultSortHash()
            );

            Sort differentSort = Sort.by(
                    Sort.Order.asc("eventType"),
                    Sort.Order.desc("id")
            );

            assertThrows(
                    InvalidAuditEventQuerySnapshotException.class,
                    () -> service.findPage(
                            emptyFilter(),
                            PageRequest.of(0, PAGE_SIZE, differentSort),
                            SNAPSHOT_ID.toString()
                    )
            );

            verifyNoInteractions(itemRepository, auditEventQueryRepository);
        }

        @Test
        @DisplayName("Should accept a different page size than the one used to create the snapshot")
        void shouldAcceptDifferentPageSize() {
            stubCurrentUser();

            stubActiveSnapshot(
                    emptyFilterHash(),
                    defaultSortHash()
            );

            when(itemRepository.findEventIds(SNAPSHOT_ID, 0, PAGE_SIZE + 1))
                    .thenReturn(List.of(101L, 102L, 103L));

            when(auditEventQueryRepository.findByIdIn(
                    List.of(101L, 102L, 103L)
            )).thenReturn(List.of(
                    createEvent(101L),
                    createEvent(102L),
                    createEvent(103L)
            ));

            var result = service.findPage(
                    emptyFilter(),
                    PageRequest.of(0, PAGE_SIZE + 1, DEFAULT_SORT),
                    SNAPSHOT_ID.toString()
            );

            assertEquals(3, result.page().getContent().size());
            assertEquals(3, result.page().getTotalElements());
            assertEquals(1, result.page().getTotalPages());
        }

        @Test
        @DisplayName("Should reject a page size above the maximum")
        void shouldRejectPageSizeAboveMaximum() {
            assertThrows(
                    InvalidAuditEventQuerySnapshotException.class,
                    () -> service.findPage(
                            emptyFilter(),
                            PageRequest.of(
                                    0,
                                    AuditEventQueryPolicy.MAX_PAGE_SIZE + 1
                            ),
                            SNAPSHOT_ID.toString()
                    )
            );

            verifyNoInteractions(
                    currentUserService,
                    snapshotRepository,
                    itemRepository,
                    auditEventQueryRepository
            );
        }

        @Test
        @DisplayName("Should reject an invalid date range before reading the snapshot")
        void shouldRejectInvalidDateRange() {
            assertThrows(
                    InvalidDateRangeException.class,
                    () -> service.findPage(
                            dates(TODAY, TODAY.plusDays(1)),
                            PageRequest.of(0, PAGE_SIZE, DEFAULT_SORT),
                            SNAPSHOT_ID.toString()
                    )
            );

            verifyNoInteractions(
                    currentUserService,
                    snapshotRepository,
                    itemRepository,
                    auditEventQueryRepository
            );
        }
    }

    private void stubCurrentUser() {
        stubCurrentUser(ACTOR_ID);
    }

    private void stubCurrentUser(Long actorId) {
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(currentUser.getId()).thenReturn(actorId);
    }

    private void stubActiveSnapshot(
            byte[] filterHash,
            byte[] sortHash
    ) {
        AuditEventQuerySnapshot snapshot = mock(
                AuditEventQuerySnapshot.class
        );

        when(snapshot.getSnapshotId()).thenReturn(SNAPSHOT_ID);
        when(snapshot.getTotalElements()).thenReturn(3);
        when(snapshot.getFilterHash()).thenReturn(filterHash);
        when(snapshot.getSortHash()).thenReturn(sortHash);

        when(snapshotRepository.findActiveByIdAndActor(
                SNAPSHOT_ID,
                ACTOR_ID,
                NOW
        )).thenReturn(Optional.of(snapshot));
    }

    /**
     * Configures only the metadata accessed before a validation failure.
     * This avoids unnecessary stubbings under Mockito strict mode.
     */
    private void stubActiveSnapshotForValidation(
            byte[] filterHash,
            byte[] sortHash
    ) {
        AuditEventQuerySnapshot snapshot = mock(
                AuditEventQuerySnapshot.class
        );

        when(snapshot.getFilterHash()).thenReturn(filterHash);
        when(snapshot.getSortHash()).thenReturn(sortHash);

        when(snapshotRepository.findActiveByIdAndActor(
                SNAPSHOT_ID,
                ACTOR_ID,
                NOW
        )).thenReturn(Optional.of(snapshot));
    }

    private static AuditEventQueryFilter dates(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new AuditEventQueryFilter(
                startDate,
                endDate,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static AuditEventQueryFilter emptyFilter() {
        return new AuditEventQueryFilter(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static byte[] emptyFilterHash() {
        return AuditEventQuerySnapshotFingerprint.filters(
                emptyFilter()
        );
    }

    private static byte[] defaultSortHash() {
        return AuditEventQuerySnapshotFingerprint.sort(DEFAULT_SORT);
    }

    private static AuditEvent createEvent(Long id) {
        AuditEvent event = AuditEvent.create(
                AuditEventType.USER_CREATED,
                AuditOutcome.SUCCESS,
                AuditActorType.USER,
                ACTOR_ID,
                "auditor",
                AuditEntityType.USER,
                "100",
                UUID.randomUUID(),
                NOW
        );

        ReflectionTestUtils.setField(event, "id", id);

        return event;
    }
}
