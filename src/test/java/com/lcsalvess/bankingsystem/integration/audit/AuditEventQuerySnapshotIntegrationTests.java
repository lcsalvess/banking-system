package com.lcsalvess.bankingsystem.integration.audit;

import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDTO;
import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.audit.InvalidAuditEventQuerySnapshotException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventRepository;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventPageResult;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventQueryFilter;
import com.lcsalvess.bankingsystem.service.audit.policy.AuditEventQueryPolicy;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotService;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest
@DisplayName("Audit event query snapshot integration tests")
class AuditEventQuerySnapshotIntegrationTests {

    private static final int PAGE_SIZE = 2;

    private static final Sort SNAPSHOT_SORT = Sort.by(
            Sort.Order.asc("createdAt")
    );

    private final List<UUID> createdSnapshotIds = new ArrayList<>();

    @Autowired
    private AuditEventQuerySnapshotService snapshotService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        createdSnapshotIds.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        try {
            for (UUID snapshotId : createdSnapshotIds) {
                jdbcTemplate.update(
                        """
                        DELETE FROM audit_event_query_snapshots
                        WHERE snapshot_id = ?
                        """,
                        snapshotId
                );
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Should preserve snapshot membership after new audit events are inserted")
    void shouldPreserveSnapshotMembershipAfterNewEventsAreInserted() {
        User auditor = createAuditor();
        authenticateAs(auditor);

        Instant baseTime = Instant.now().minusSeconds(60);

        List<Long> originalEventIds = persistEvents(
                auditor,
                baseTime,
                3
        );

        Pageable firstPageRequest = PageRequest.of(
                0,
                PAGE_SIZE,
                SNAPSHOT_SORT
        );

        AuditEventPageResult firstPage = snapshotService.createSnapshot(
                filterFor(auditor),
                firstPageRequest
        );

        UUID snapshotId = trackSnapshot(firstPage.snapshotToken());

        assertEquals(
                originalEventIds.subList(0, 2),
                extractEventIds(firstPage)
        );

        assertEquals(3, firstPage.page().getTotalElements());
        assertEquals(2, firstPage.page().getTotalPages());

        /*
         * A new matching event is committed after the snapshot was created.
         * It must not become part of the existing materialized result.
         */
        List<Long> newEventIds = persistEvents(
                auditor,
                baseTime.plusSeconds(10),
                1
        );

        assertEquals(1, newEventIds.size());

        Pageable secondPageRequest = PageRequest.of(
                1,
                PAGE_SIZE,
                SNAPSHOT_SORT
        );

        AuditEventPageResult secondPage = snapshotService.findPage(
                filterFor(auditor),
                secondPageRequest,
                firstPage.snapshotToken()
        );

        assertEquals(
                List.of(originalEventIds.get(2)),
                extractEventIds(secondPage)
        );

        assertEquals(3, secondPage.page().getTotalElements());
        assertEquals(firstPage.snapshotToken(), secondPage.snapshotToken());

        Integer persistedItemCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_event_query_snapshot_items
                WHERE snapshot_id = ?
                """,
                Integer.class,
                snapshotId
        );

        assertEquals(3, persistedItemCount);

        List<Integer> positions = jdbcTemplate.queryForList(
                """
                SELECT position
                FROM audit_event_query_snapshot_items
                WHERE snapshot_id = ?
                ORDER BY position
                """,
                Integer.class,
                snapshotId
        );

        assertEquals(List.of(0, 1, 2), positions);
    }

    @Test
    @DisplayName("Should prevent an auditor from accessing another user's snapshot")
    void shouldRejectSnapshotAccessFromAnotherUser() {
        User snapshotOwner = createAuditor();
        authenticateAs(snapshotOwner);

        persistEvents(
                snapshotOwner,
                Instant.now().minusSeconds(30),
                1
        );

        Pageable pageable = PageRequest.of(
                0,
                PAGE_SIZE,
                SNAPSHOT_SORT
        );

        AuditEventPageResult result = snapshotService.createSnapshot(
                filterFor(snapshotOwner),
                pageable
        );

        trackSnapshot(result.snapshotToken());

        User anotherAuditor = createAuditor();
        authenticateAs(anotherAuditor);

        InvalidAuditEventQuerySnapshotException exception =
                assertThrows(
                        InvalidAuditEventQuerySnapshotException.class,
                        () -> snapshotService.findPage(
                                filterFor(snapshotOwner),
                                pageable,
                                result.snapshotToken()
                        )
                );

        assertTrue(
                exception.getMessage() != null
                        && !exception.getMessage().isBlank()
        );
    }

    @Test
    @DisplayName("Should reject an expired snapshot")
    void shouldRejectExpiredSnapshot() {
        User auditor = createAuditor();
        authenticateAs(auditor);

        persistEvents(
                auditor,
                Instant.now().minusSeconds(30),
                1
        );

        Pageable pageable = PageRequest.of(
                0,
                PAGE_SIZE,
                SNAPSHOT_SORT
        );

        AuditEventPageResult result = snapshotService.createSnapshot(
                filterFor(auditor),
                pageable
        );

        UUID snapshotId = trackSnapshot(result.snapshotToken());

        /*
         * Move both timestamps into the past while preserving
         * the database constraint expires_at > created_at.
         */
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE audit_event_query_snapshots
                SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                    expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                WHERE snapshot_id = ?
                """,
                snapshotId
        );

        assertEquals(1, updatedRows);

        InvalidAuditEventQuerySnapshotException exception =
                assertThrows(
                        InvalidAuditEventQuerySnapshotException.class,
                        () -> snapshotService.findPage(
                                filterFor(auditor),
                                pageable,
                                result.snapshotToken()
                        )
                );

        assertTrue(
                exception.getMessage() != null
                        && !exception.getMessage().isBlank()
        );
    }

    @Test
    @DisplayName("Should allow another page size while keeping the same result set")
    void shouldAllowAnotherPageSize() {
        User auditor = createAuditor();
        authenticateAs(auditor);

        List<Long> eventIds = persistEvents(
                auditor,
                Instant.now().minusSeconds(60),
                5
        );

        AuditEventPageResult firstPage = snapshotService.createSnapshot(
                filterFor(auditor),
                PageRequest.of(0, PAGE_SIZE, SNAPSHOT_SORT)
        );

        trackSnapshot(firstPage.snapshotToken());

        AuditEventPageResult otherPage = snapshotService.findPage(
                filterFor(auditor),
                PageRequest.of(1, 3, SNAPSHOT_SORT),
                firstPage.snapshotToken()
        );

        assertEquals(eventIds.subList(3, 5), extractEventIds(otherPage));
        assertEquals(5, otherPage.page().getTotalElements());
        assertEquals(2, otherPage.page().getTotalPages());
    }

    @Test
    @DisplayName("Should keep only the most recent active snapshots of an auditor")
    void shouldLimitActiveSnapshotsPerActor() {
        User auditor = createAuditor();
        authenticateAs(auditor);

        persistEvents(auditor, Instant.now().minusSeconds(60), 1);

        Pageable pageable = PageRequest.of(0, PAGE_SIZE, SNAPSHOT_SORT);

        int limit = AuditEventQueryPolicy.MAX_ACTIVE_SNAPSHOTS_PER_ACTOR;

        List<AuditEventPageResult> created = new ArrayList<>();

        for (int i = 0; i < limit + 2; i++) {
            AuditEventPageResult result = snapshotService.createSnapshot(
                    filterFor(auditor),
                    pageable
            );

            trackSnapshot(result.snapshotToken());
            created.add(result);
        }

        assertEquals(
                limit,
                count(
                        "SELECT COUNT(*) FROM audit_event_query_snapshots "
                                + "WHERE actor_user_id = ?",
                        auditor.getId()
                )
        );

        // The two oldest snapshots were discarded together with their items.
        for (int i = 0; i < 2; i++) {
            String discarded = created.get(i).snapshotToken();

            assertThrows(
                    InvalidAuditEventQuerySnapshotException.class,
                    () -> snapshotService.findPage(
                            filterFor(auditor),
                            pageable,
                            discarded
                    )
            );

            assertEquals(
                    0,
                    count(
                            "SELECT COUNT(*) "
                                    + "FROM audit_event_query_snapshot_items "
                                    + "WHERE snapshot_id = ?",
                            UUID.fromString(discarded)
                    )
            );
        }

        // The most recent ones remain usable.
        for (int i = 2; i < created.size(); i++) {
            AuditEventPageResult page = snapshotService.findPage(
                    filterFor(auditor),
                    pageable,
                    created.get(i).snapshotToken()
            );

            assertEquals(1, page.page().getTotalElements());
        }
    }

    @Test
    @DisplayName("Should not discard the snapshots of another auditor")
    void shouldNotDiscardSnapshotsOfAnotherAuditor() {
        User other = createAuditor();
        authenticateAs(other);

        persistEvents(other, Instant.now().minusSeconds(60), 1);

        AuditEventPageResult otherSnapshot = snapshotService.createSnapshot(
                filterFor(other),
                PageRequest.of(0, PAGE_SIZE, SNAPSHOT_SORT)
        );

        trackSnapshot(otherSnapshot.snapshotToken());

        User auditor = createAuditor();
        authenticateAs(auditor);

        for (int i = 0;
             i < AuditEventQueryPolicy.MAX_ACTIVE_SNAPSHOTS_PER_ACTOR + 1;
             i++) {
            trackSnapshot(
                    snapshotService.createSnapshot(
                            filterFor(auditor),
                            PageRequest.of(0, PAGE_SIZE, SNAPSHOT_SORT)
                    ).snapshotToken()
            );
        }

        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM audit_event_query_snapshots "
                                + "WHERE snapshot_id = ?",
                        UUID.fromString(otherSnapshot.snapshotToken())
                )
        );
    }

    private int count(String sql, Object argument) {
        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                argument
        );

        return count == null ? 0 : count;
    }

    private User createAuditor() {
        String username = "aud"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        String email = username + "@example.test";

        String employeeId = "EMP-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        return transactionTemplate.execute(status ->
                userRepository.save(
                        UserTestFactory.aUser()
                                .withEmployeeId(employeeId)
                                .withFullName("Snapshot Integration Auditor")
                                .withUsername(username)
                                .withEmail(email)
                                .withEncodedPassword("test-password-hash")
                                .withRole(Role.AUDITOR)
                                .build()
                )
        );
    }

    private void authenticateAs(User user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private List<Long> persistEvents(
            User actor,
            Instant baseTime,
            int quantity
    ) {
        return transactionTemplate.execute(status -> {
            List<Long> ids = new ArrayList<>();

            for (int index = 0; index < quantity; index++) {
                AuditEvent event = AuditEvent.create(
                        AuditEventType.USER_CREATED,
                        AuditOutcome.SUCCESS,
                        AuditActorType.USER,
                        actor.getId(),
                        actor.getUsername(),
                        AuditEntityType.USER,
                        "snapshot-test-" + UUID.randomUUID(),
                        UUID.randomUUID(),
                        baseTime.plusSeconds(index)
                );

                AuditEvent savedEvent =
                        auditEventRepository.save(event);

                ids.add(savedEvent.getId());
            }

            return List.copyOf(ids);
        });
    }

    private UUID trackSnapshot(String snapshotToken) {
        UUID snapshotId = UUID.fromString(snapshotToken);
        createdSnapshotIds.add(snapshotId);
        return snapshotId;
    }

    private static AuditEventQueryFilter filterFor(User actor) {
        return new AuditEventQueryFilter(
                null,
                null,
                null,
                null,
                null,
                actor.getUsername(),
                null,
                null
        );
    }

    private static List<Long> extractEventIds(
            AuditEventPageResult result
    ) {
        return result.page()
                .getContent()
                .stream()
                .map(AuditEventResponseDTO::id)
                .toList();
    }
}
