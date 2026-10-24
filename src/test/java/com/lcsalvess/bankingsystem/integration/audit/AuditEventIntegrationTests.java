package com.lcsalvess.bankingsystem.integration.audit;

import com.lcsalvess.bankingsystem.event.account.AccountOperationEvent;
import com.lcsalvess.bankingsystem.event.account.AccountOperationType;
import com.lcsalvess.bankingsystem.event.client.ClientOperationEvent;
import com.lcsalvess.bankingsystem.event.client.ClientOperationType;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova no PostgreSQL que a trilha é append-only e que a gravação acompanha a
 * transação da operação. Como a tabela é imutável, o teste de commit deixa uma
 * linha (com entity_id aleatório) no banco de teste; todos os outros cenários
 * terminam em rollback e não deixam nada.
 */
@ActiveProfiles("test")
@SpringBootTest
class AuditEventIntegrationTests {

    private static final String APPEND_ONLY_MESSAGE = "append-only";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Database should reject UPDATE on audit_events")
    void shouldRejectUpdate() {
        String entityId = uniqueEntityId();

        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    insertAuditRow(entityId);
                    jdbcTemplate.update(
                            "UPDATE audit_events SET outcome = 'FAILURE' WHERE entity_id = ?",
                            entityId
                    );
                })
        );

        assertAppendOnly(exception);
        assertEquals(0, countByEntityId(entityId));
    }

    @Test
    @DisplayName("Database should reject DELETE on audit_events")
    void shouldRejectDelete() {
        String entityId = uniqueEntityId();

        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    insertAuditRow(entityId);
                    jdbcTemplate.update(
                            "DELETE FROM audit_events WHERE entity_id = ?",
                            entityId
                    );
                })
        );

        assertAppendOnly(exception);
        assertEquals(0, countByEntityId(entityId));
    }

    @Test
    @DisplayName("Database should reject TRUNCATE on audit_events")
    void shouldRejectTruncate() {
        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> transactionTemplate.executeWithoutResult(status ->
                        jdbcTemplate.execute("TRUNCATE TABLE audit_events")
                )
        );

        assertAppendOnly(exception);
    }

    @Test
    @DisplayName("Should write the audit row when the operation commits")
    void shouldWriteAuditRowWhenOperationCommits() {
        long userId = randomUserId();

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(
                        new UserOperationEvent(userId, UserOperationType.CREATED)
                )
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT event_type, outcome, actor_type, actor_user_id, entity_type
                FROM audit_events
                WHERE entity_id = ?
                """,
                String.valueOf(userId)
        );

        assertEquals("USER_CREATED", row.get("event_type"));
        assertEquals("SUCCESS", row.get("outcome"));
        assertEquals("ANONYMOUS", row.get("actor_type"));
        assertNull(row.get("actor_user_id"));
        assertEquals("USER", row.get("entity_type"));
    }

    @Test
    @DisplayName("Should write the audit row when a client creation commits")
    void shouldWriteAuditRowWhenClientCreationCommits() {
        long clientId = randomUserId();

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(
                        new ClientOperationEvent(
                                clientId,
                                ClientOperationType.CREATED,
                                "lucas"
                        )
                )
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_type, outcome, entity_type FROM audit_events WHERE entity_id = ?",
                String.valueOf(clientId)
        );

        assertEquals("CLIENT_CREATED", row.get("event_type"));
        assertEquals("SUCCESS", row.get("outcome"));
        assertEquals("CLIENT", row.get("entity_type"));
    }

    @Test
    @DisplayName("Should write the audit row when an account creation commits")
    void shouldWriteAuditRowWhenAccountCreationCommits() {
        long accountId = randomUserId();

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(
                        new AccountOperationEvent(
                                accountId,
                                AccountOperationType.CREATED,
                                "lucas"
                        )
                )
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_type, outcome, entity_type FROM audit_events WHERE entity_id = ?",
                String.valueOf(accountId)
        );

        assertEquals("ACCOUNT_CREATED", row.get("event_type"));
        assertEquals("SUCCESS", row.get("outcome"));
        assertEquals("ACCOUNT", row.get("entity_type"));
    }

    @Test
    @DisplayName("Should not write the audit row when a client or account operation rolls back")
    void shouldNotWriteAuditRowWhenClientOrAccountOperationRollsBack() {
        long clientId = randomUserId();
        long accountId = randomUserId();

        assertThrows(
                IllegalStateException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    eventPublisher.publishEvent(
                            new ClientOperationEvent(
                                    clientId,
                                    ClientOperationType.UPDATED,
                                    "lucas"
                            )
                    );
                    eventPublisher.publishEvent(
                            new AccountOperationEvent(
                                    accountId,
                                    AccountOperationType.CANCELLED,
                                    "lucas"
                            )
                    );
                    throw new IllegalStateException("forced rollback");
                })
        );

        assertEquals(0, countByEntityId(String.valueOf(clientId)));
        assertEquals(0, countByEntityId(String.valueOf(accountId)));
    }

    @Test
    @DisplayName("Should not write the audit row when the operation rolls back")
    void shouldNotWriteAuditRowWhenOperationRollsBack() {
        long userId = randomUserId();

        assertThrows(
                IllegalStateException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    eventPublisher.publishEvent(
                            new UserOperationEvent(userId, UserOperationType.CREATED)
                    );
                    throw new IllegalStateException("forced rollback");
                })
        );

        assertEquals(0, countByEntityId(String.valueOf(userId)));
    }

    private void insertAuditRow(String entityId) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_events
                    (event_type, outcome, actor_type, entity_type, entity_id)
                VALUES ('USER_CREATED', 'SUCCESS', 'ANONYMOUS', 'USER', ?)
                """,
                entityId
        );
    }

    private int countByEntityId(String entityId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_events WHERE entity_id = ?",
                Integer.class,
                entityId
        );

        return count == null ? 0 : count;
    }

    private void assertAppendOnly(DataAccessException exception) {
        assertTrue(
                exception.getMessage().contains(APPEND_ONLY_MESSAGE),
                "The statement failed for another reason: "
                        + exception.getMessage()
        );
    }

    private String uniqueEntityId() {
        return "audit-test-" + UUID.randomUUID();
    }

    private long randomUserId() {
        return ThreadLocalRandom.current()
                .nextLong(1_000_000_000L, Long.MAX_VALUE);
    }
}
