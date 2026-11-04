package com.lcsalvess.bankingsystem.integration.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventRepository;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventPageResult;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventQueryFilter;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotCleanupJob;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("test")
@SpringBootTest
@DisplayName("Audit event query snapshot cleanup integration tests")
class AuditEventQuerySnapshotCleanupIntegrationTests {

    private static final int PAGE_SIZE = 20;

    private static final Sort SNAPSHOT_SORT = Sort.by(
            Sort.Order.asc("createdAt")
    );

    private final List<UUID> createdSnapshotIds = new ArrayList<>();

    @Autowired
    private AuditEventQuerySnapshotCleanupJob cleanupJob;

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
    @DisplayName("Should remove expired snapshots and cascade their items")
    void shouldRemoveExpiredSnapshotsAndCascadeItems() {
        User auditor = createAuditor();
        authenticateAs(auditor);

        persistEvent(auditor);

        AuditEventPageResult expiredSnapshot = createSnapshot(auditor);
        AuditEventPageResult activeSnapshot = createSnapshot(auditor);

        UUID expiredSnapshotId =
                trackSnapshot(expiredSnapshot.snapshotToken());

        UUID activeSnapshotId =
                trackSnapshot(activeSnapshot.snapshotToken());

        assertEquals(1, countSnapshot(expiredSnapshotId));
        assertEquals(1, countSnapshotItems(expiredSnapshotId));

        assertEquals(1, countSnapshot(activeSnapshotId));
        assertEquals(1, countSnapshotItems(activeSnapshotId));

        expireSnapshot(expiredSnapshotId);

        /*
         * Invoke the Spring-managed job bean.
         * This exercises the transactional proxy and the repository's
         * Propagation.MANDATORY requirement.
         */
        cleanupJob.cleanUp();

        assertEquals(0, countSnapshot(expiredSnapshotId));
        assertEquals(0, countSnapshotItems(expiredSnapshotId));

        /*
         * Active snapshots and their items must remain untouched.
         */
        assertEquals(1, countSnapshot(activeSnapshotId));
        assertEquals(1, countSnapshotItems(activeSnapshotId));
    }

    @Test
    @DisplayName("Should preserve active snapshots when no snapshots have expired")
    void shouldPreserveActiveSnapshots() {
        User auditor = createAuditor();
        authenticateAs(auditor);

        persistEvent(auditor);

        AuditEventPageResult activeSnapshot = createSnapshot(auditor);

        UUID snapshotId = trackSnapshot(activeSnapshot.snapshotToken());

        assertEquals(1, countSnapshot(snapshotId));
        assertEquals(1, countSnapshotItems(snapshotId));

        cleanupJob.cleanUp();

        assertEquals(1, countSnapshot(snapshotId));
        assertEquals(1, countSnapshotItems(snapshotId));
    }

    private User createAuditor() {
        String username = "aud"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        String email = username + "@example.test";

        User savedUser = transactionTemplate.execute(status ->
                userRepository.save(
                        new User(
                                username,
                                email,
                                "test-password-hash",
                                Role.AUDITOR
                        )
                )
        );

        assertNotNull(savedUser);
        assertNotNull(savedUser.getId());

        return savedUser;
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

    private void persistEvent(User auditor) {
        transactionTemplate.executeWithoutResult(status -> {
            AuditEvent event = AuditEvent.create(
                    AuditEventType.USER_CREATED,
                    AuditOutcome.SUCCESS,
                    AuditActorType.USER,
                    auditor.getId(),
                    auditor.getUsername(),
                    AuditEntityType.USER,
                    "snapshot-cleanup-" + UUID.randomUUID(),
                    UUID.randomUUID(),
                    Instant.now().minusSeconds(30)
            );

            auditEventRepository.save(event);
        });
    }

    private AuditEventPageResult createSnapshot(User auditor) {
        AuditEventQueryFilter filter = new AuditEventQueryFilter(
                null,
                null,
                null,
                null,
                null,
                auditor.getUsername(),
                null,
                null
        );

        return snapshotService.createSnapshot(
                filter,
                PageRequest.of(
                        0,
                        PAGE_SIZE,
                        SNAPSHOT_SORT
                )
        );
    }

    private void expireSnapshot(UUID snapshotId) {
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
    }

    private int countSnapshot(UUID snapshotId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_event_query_snapshots
                WHERE snapshot_id = ?
                """,
                Integer.class,
                snapshotId
        );

        return count == null ? 0 : count;
    }

    private int countSnapshotItems(UUID snapshotId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_event_query_snapshot_items
                WHERE snapshot_id = ?
                """,
                Integer.class,
                snapshotId
        );

        return count == null ? 0 : count;
    }

    private UUID trackSnapshot(String snapshotToken) {
        UUID snapshotId = UUID.fromString(snapshotToken);
        createdSnapshotIds.add(snapshotId);
        return snapshotId;
    }
}
