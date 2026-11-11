package com.lcsalvess.bankingsystem.integration.audit;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
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
 * End-to-end tests (HTTP, filters, services, and PostgreSQL) verifying
 * authentication events and HTTP request outcomes.
 *
 * <p>Business authentication events and HTTP completion events are
 * independent records linked by the same correlation ID.
 *
 * <p>Since the audit table is immutable, each test uses a unique username
 * and queries only its own records.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuditSecurityEventsIntegrationTests {

    private static final String PASSWORD = "Currentpassword123!";

    // Documentation range (RFC 5737): rate-limit bucket reserved for this test.
    private static final String CLIENT_IP = "198.51.100.77";

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginRateLimitProperties loginRateLimitProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String username;
    private Long userId;

    @BeforeEach
    void setUp() {
        username = "it" + UUID.randomUUID().toString().substring(0, 8);

        String employeeId = "EMP-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        User user = userRepository.save(User.createHuman(
                employeeId,
                "Integration Test Employee",
                username,
                username + "@test.com",
                passwordEncoder.encode(PASSWORD),
                Role.EMPLOYEE
        ));

        userId = user.getId();
    }

    @Test
    @DisplayName("Should record failed login and its actual HTTP response")
    void shouldRecordFailedLogin() throws Exception {
        ResultActions result = login("wrong-password")
                .andExpect(status().isUnauthorized());

        UUID correlationId = correlationIdOf(result);

        Map<String, Object> loginRow = single("LOGIN_FAILURE");

        assertEquals("FAILURE", loginRow.get("outcome"));
        assertEquals("ANONYMOUS", loginRow.get("actor_type"));
        assertNull(loginRow.get("actor_user_id"));
        assertEquals("AUTHENTICATION", loginRow.get("entity_type"));
        assertEquals("INVALID_CREDENTIALS", loginRow.get("reason"));
        assertEquals(CLIENT_IP, loginRow.get("ip_address_text"));

        Map<String, Object> httpRow =
                singleHttpRequest(correlationId);

        assertEquals(
                "HTTP_REQUEST_COMPLETED",
                httpRow.get("event_type")
        );
        assertEquals("DENIED", httpRow.get("outcome"));
        assertEquals("ANONYMOUS", httpRow.get("actor_type"));
        assertEquals("ENDPOINT", httpRow.get("entity_type"));
        assertEquals(
                "POST " + LOGIN_PATH,
                httpRow.get("entity_id")
        );
        assertEquals("UNAUTHORIZED", httpRow.get("reason"));
        assertEquals(401, httpRow.get("http_status"));
        assertEquals(CLIENT_IP, httpRow.get("ip_address_text"));
        assertEquals(correlationId, httpRow.get("correlation_id"));

        assertEquals(
                loginRow.get("correlation_id"),
                httpRow.get("correlation_id")
        );
    }

    @Test
    @DisplayName("Should record successful login and its actual HTTP response")
    void shouldRecordSuccessfulLogin() throws Exception {
        ResultActions result = login(PASSWORD)
                .andExpect(status().isOk());

        UUID correlationId = correlationIdOf(result);

        Map<String, Object> loginRow = single("LOGIN_SUCCESS");

        assertEquals("SUCCESS", loginRow.get("outcome"));
        assertEquals("USER", loginRow.get("actor_type"));
        assertEquals(
                userId,
                ((Number) loginRow.get("actor_user_id")).longValue()
        );
        assertEquals(CLIENT_IP, loginRow.get("ip_address_text"));
        assertNull(loginRow.get("reason"));

        Map<String, Object> httpRow =
                singleHttpRequest(correlationId);

        assertEquals(
                "HTTP_REQUEST_COMPLETED",
                httpRow.get("event_type")
        );
        assertEquals("SUCCESS", httpRow.get("outcome"));
        assertEquals("ANONYMOUS", httpRow.get("actor_type"));
        assertEquals("ENDPOINT", httpRow.get("entity_type"));
        assertEquals(
                "POST " + LOGIN_PATH,
                httpRow.get("entity_id")
        );
        assertEquals(200, httpRow.get("http_status"));
        assertEquals(CLIENT_IP, httpRow.get("ip_address_text"));
        assertNull(httpRow.get("reason"));
        assertEquals(correlationId, httpRow.get("correlation_id"));

        assertEquals(
                loginRow.get("correlation_id"),
                httpRow.get("correlation_id")
        );
    }

    @Test
    @DisplayName("Should record URL authorization denial as an HTTP request outcome")
    void shouldRecordAccessDenied() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(get("/api/v1/audit-events")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        }))
                .andExpect(status().isForbidden());

        Map<String, Object> row =
                single("HTTP_REQUEST_COMPLETED");

        assertEquals("DENIED", row.get("outcome"));
        assertEquals("USER", row.get("actor_type"));
        assertEquals("ENDPOINT", row.get("entity_type"));
        assertEquals(
                "GET /api/v1/audit-events",
                row.get("entity_id")
        );
        assertEquals("FORBIDDEN", row.get("reason"));
        assertEquals(403, row.get("http_status"));
        assertEquals(CLIENT_IP, row.get("ip_address_text"));
        assertNotNull(row.get("correlation_id"));
    }

    @Test
    @DisplayName("Should record business failure as an HTTP request outcome")
    void shouldRecordFailedOperation() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(patch("/api/v1/auth/password")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
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

        Map<String, Object> row =
                single("HTTP_REQUEST_COMPLETED");

        assertEquals("FAILURE", row.get("outcome"));
        assertEquals("USER", row.get("actor_type"));
        assertEquals("ENDPOINT", row.get("entity_type"));
        assertEquals(
                "PATCH /api/v1/auth/password",
                row.get("entity_id")
        );
        assertEquals(
                "INVALID_CURRENT_PASSWORD",
                row.get("reason")
        );
        assertEquals(400, row.get("http_status"));
        assertEquals(CLIENT_IP, row.get("ip_address_text"));
        assertNotNull(row.get("correlation_id"));
    }

    @Test
    @DisplayName("Should not record HTTP outcomes for rejected anonymous requests outside login")
    void shouldNotRecordAnonymousRejections() throws Exception {
        int before = countHttpRequestEvents();

        mockMvc.perform(get("/api/v1/clients")
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        }))
                .andExpect(status().isUnauthorized());

        assertEquals(before, countHttpRequestEvents());
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

    @Test
    @DisplayName("Should deduplicate repeated rate-limited login audit events")
    void shouldDeduplicateRepeatedLoginBlocks() throws Exception {
        int maxFailedAttempts = loginRateLimitProperties.maxFailedAttempts();

        for (int i = 0; i < maxFailedAttempts; i++) {
            login("wrong-password")
                    .andExpect(status().isUnauthorized());
        }

        ResultActions firstBlocked = login("wrong-password")
                .andExpect(status().isTooManyRequests());

        UUID firstCorrelationId = correlationIdOf(firstBlocked);

        ResultActions repeatedBlocked = login("wrong-password")
                .andExpect(status().isTooManyRequests());

        UUID repeatedCorrelationId = correlationIdOf(repeatedBlocked);

        Map<String, Object> row = single("LOGIN_BLOCKED");

        assertEquals("DENIED", row.get("outcome"));
        assertEquals("ANONYMOUS", row.get("actor_type"));
        assertEquals("RATE_LIMIT_USERNAME_AND_IP", row.get("reason"));
        assertEquals(429, row.get("http_status"));
        assertEquals(CLIENT_IP, row.get("ip_address_text"));
        assertEquals(firstCorrelationId, row.get("correlation_id"));

        assertEquals(1, countByEventTypeAndUsername("LOGIN_BLOCKED"));

        assertEquals(0, countHttpRequestEvents(firstCorrelationId));
        assertEquals(0, countHttpRequestEvents(repeatedCorrelationId));
    }

    private void insert(
            String outcome,
            String reason,
            String ipAddress
    ) {
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

    /**
     * Finds an event associated with the current test user.
     * HTTP completion records for authenticated requests retain the actor
     * username, while business authentication events retain their own entity ID.
     */
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

        assertEquals(
                1,
                rows.size(),
                "Rows for " + eventType + ": " + rows
        );

        return rows.getFirst();
    }

    /**
     * Login requests are anonymous at the HTTP layer. Their HTTP completion
     * event is therefore located using the correlation ID returned by the API.
     */
    private Map<String, Object> singleHttpRequest(UUID correlationId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT audit_events.*,
                       host(ip_address) AS ip_address_text
                FROM audit_events
                WHERE event_type = 'HTTP_REQUEST_COMPLETED'
                  AND correlation_id = ?
                """,
                correlationId
        );

        assertEquals(
                1,
                rows.size(),
                "HTTP request events for correlation ID "
                        + correlationId + ": " + rows
        );

        return rows.getFirst();
    }

    private int countByEventTypeAndUsername(String eventType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM audit_events
                WHERE event_type = ?
                  AND actor_username = ?
                """,
                Integer.class,
                eventType,
                username
        );

        return count == null ? 0 : count;
    }

    private int countHttpRequestEvents(UUID correlationId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM audit_events
                WHERE event_type = 'HTTP_REQUEST_COMPLETED'
                  AND correlation_id = ?
                """,
                Integer.class,
                correlationId
        );

        return count == null ? 0 : count;
    }

    private int countHttpRequestEvents() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM audit_events
                WHERE event_type = 'HTTP_REQUEST_COMPLETED'
                """,
                Integer.class
        );

        return count == null ? 0 : count;
    }

    private UUID correlationIdOf(ResultActions result) throws Exception {
        String header = result.andReturn()
                .getResponse()
                .getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertNotNull(header, "Correlation ID response header is missing");

        return UUID.fromString(header);
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post(LOGIN_PATH)
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
