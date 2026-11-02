package com.lcsalvess.bankingsystem.integration.audit;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests (HTTP, filters, services, and PostgreSQL) verifying that
 * login events, access denials, and operation failures are recorded in the
 * audit trail. Since the audit table is immutable, each test uses a unique
 * username and queries only its own records.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuditSecurityEventsIntegrationTests {

    private static final String PASSWORD = "Currentpassword123!";

    // Documentation range (RFC 5737): rate-limit bucket reserved for this test.
    private static final String CLIENT_IP = "198.51.100.77";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String username;
    private Long userId;

    @BeforeEach
    void setUp() {
        username = "it" + UUID.randomUUID().toString().substring(0, 8);

        User user = userRepository.save(new User(
                username,
                username + "@test.com",
                passwordEncoder.encode(PASSWORD),
                Role.EMPLOYEE
        ));

        userId = user.getId();
    }

    @Test
    @DisplayName("Should record failed login as anonymous LOGIN_FAILURE with IP address")
    void shouldRecordFailedLogin() throws Exception {
        login("wrong-password").andExpect(status().isUnauthorized());

        Map<String, Object> row = single("LOGIN_FAILURE");

        assertEquals("FAILURE", row.get("outcome"));
        assertEquals("ANONYMOUS", row.get("actor_type"));
        assertNull(row.get("actor_user_id"));
        assertEquals("AUTHENTICATION", row.get("entity_type"));
        assertEquals("INVALID_CREDENTIALS", row.get("reason"));
        assertEquals(CLIENT_IP, row.get("ip_address_text"));
    }

    @Test
    @DisplayName("Should record successful login as LOGIN_SUCCESS for the authenticated user")
    void shouldRecordSuccessfulLogin() throws Exception {
        login(PASSWORD).andExpect(status().isOk());

        Map<String, Object> row = single("LOGIN_SUCCESS");

        assertEquals("SUCCESS", row.get("outcome"));
        assertEquals("USER", row.get("actor_type"));
        assertEquals(userId, ((Number) row.get("actor_user_id")).longValue());
        assertEquals(CLIENT_IP, row.get("ip_address_text"));
        assertNull(row.get("reason"));
    }

    @Test
    @DisplayName("Should record URL authorization denial as ACCESS_DENIED for the authenticated user")
    void shouldRecordAccessDenied() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(get("/api/v1/audit-events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        }))
                .andExpect(status().isForbidden());

        Map<String, Object> row = single("ACCESS_DENIED");

        assertEquals("DENIED", row.get("outcome"));
        assertEquals("USER", row.get("actor_type"));
        assertEquals("ENDPOINT", row.get("entity_type"));
        assertEquals("GET /api/v1/audit-events", row.get("entity_id"));
        assertEquals("FORBIDDEN", row.get("reason"));
        assertEquals(403, row.get("http_status"));
        assertEquals(CLIENT_IP, row.get("ip_address_text"));
        assertNotNull(row.get("correlation_id"));
    }

    @Test
    @DisplayName("Should record business failure on a data-modifying operation as OPERATION_FAILED")
    void shouldRecordFailedOperation() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(patch("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currentPassword": "wrong-password",
                                    "newPassword": "BrandNewPassword456!"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        }))
                .andExpect(status().isBadRequest());

        Map<String, Object> row = single("OPERATION_FAILED");

        assertEquals("FAILURE", row.get("outcome"));
        assertEquals("PATCH /api/v1/auth/password", row.get("entity_id"));
        assertEquals("INVALID_CURRENT_PASSWORD", row.get("reason"));
        assertEquals(400, row.get("http_status"));
        assertEquals(CLIENT_IP, row.get("ip_address_text"));
    }

    @Test
    @DisplayName("Should not record audit events for rejected anonymous requests")
    void shouldNotRecordAnonymousRejections() throws Exception {
        int before = countOperationalFailures();

        mockMvc.perform(get("/api/v1/clients").with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    return request;
                }))
                .andExpect(status().isUnauthorized());

        assertEquals(before, countOperationalFailures());
    }

    @Test
    @DisplayName("Should persist the IP address column using PostgreSQL INET")
    void ipAddressColumnShouldUseInet() {
        String columnType = jdbcTemplate.queryForObject(
                """
                SELECT udt_name
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'audit_events'
                  AND column_name = 'ip_address'
                """,
                String.class
        );

        assertEquals("inet", columnType);
    }

    @Test
    @DisplayName("Database should reject malformed reasons, missing reasons, and non-host IP addresses")
    void databaseShouldRejectInvalidAuditData() {
        // Reason must follow the expected uppercase code format.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insert("FAILURE", "not a valid reason", null)
        );

        // Every non-success event must specify a reason.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insert("FAILURE", null, null)
        );

        // INET accepts network addresses, but the audit trail only permits hosts.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insert("DENIED", "FORBIDDEN", "1.2.3.4/24")
        );
    }

    private void insert(String outcome, String reason, String ipAddress) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_events
                    (event_type, outcome, actor_type, entity_type, entity_id,
                     reason, ip_address)
                VALUES (
                    'OPERATION_FAILED',
                    ?,
                    'ANONYMOUS',
                    'ENDPOINT',
                    ?,
                    ?,
                    ?::inet
                )
                """,
                outcome,
                "constraint-test-" + UUID.randomUUID(),
                reason,
                ipAddress
        );
    }

    private Map<String, Object> single(String eventType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT audit_events.*,
                       host(ip_address) AS ip_address_text
                FROM audit_events
                WHERE event_type = ?
                  AND (actor_username = ? OR entity_id = ?)
                """,
                eventType,
                username,
                username
        );

        // ACCESS_DENIED and OPERATION_FAILED use the endpoint as entity_id.
        // The unique username identifies the actor associated with those events.
        assertEquals(1, rows.size(), "Rows for " + eventType + ": " + rows);

        return rows.getFirst();
    }

    private int countOperationalFailures() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM audit_events
                WHERE event_type IN ('ACCESS_DENIED', 'OPERATION_FAILED')
                """,
                Integer.class
        );

        return count == null ? 0 : count;
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "username": "%s",
                            "password": "%s"
                        }
                        """.formatted(username, password))
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    return request;
                }));
    }

    private String loginAndGetToken() throws Exception {
        String json = login(PASSWORD)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return com.jayway.jsonpath.JsonPath.read(json, "$.token");
    }
}
