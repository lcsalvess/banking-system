package com.lcsalvess.bankingsystem.integration.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest
@DisplayName("audit_events query indexes integration tests")
class AuditEventIndexesIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Should define the indexes that support the audit query filters")
    void shouldDefineQuerySupportIndexes() {
        List<String> names = jdbcTemplate.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'audit_events'
                """,
                String.class
        );

        assertTrue(names.containsAll(List.of(
                "idx_audit_events_created_at",
                "idx_audit_events_event_type_created_at",
                "idx_audit_events_outcome_created_at",
                "idx_audit_events_actor_username_prefix"
        )));
    }

    @Test
    @DisplayName("Should index the lowercased username with text_pattern_ops")
    void shouldIndexLowercasedUsernameForPrefixSearch() {
        String definition = jdbcTemplate.queryForObject(
                """
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'idx_audit_events_actor_username_prefix'
                """,
                String.class
        );

        assertNotNull(definition);
        assertTrue(definition.contains("lower((actor_username)::text)"));
        assertTrue(definition.contains("text_pattern_ops"));
    }
}
