package com.lcsalvess.bankingsystem.integration.audit;

import com.jayway.jsonpath.JsonPath;
import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.repository.audit.AuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests (HTTP, security, services, and PostgreSQL) for the
 * snapshot-based pagination of {@code GET /api/v1/audit-events}.
 *
 * <p>Audit events are immutable, so each test filters by an entity ID that
 * only its own events carry. The snapshots of the auditors created here are
 * removed after each test.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Audit event snapshot endpoint integration tests")
class AuditEventSnapshotEndpointIntegrationTests {

    private static final String PASSWORD = "Currentpassword123!";

    // Documentation range (RFC 5737): rate-limit bucket reserved for this test.
    private static final String CLIENT_IP = "198.51.100.79";

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String AUDIT_PATH = "/api/v1/audit-events";
    private static final String SNAPSHOT_HEADER = "X-Audit-Snapshot";

    private final List<Long> createdAuditorIds = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String entityId;

    @BeforeEach
    void setUp() {
        createdAuditorIds.clear();
        entityId = "snapshot-e2e-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        for (Long auditorId : createdAuditorIds) {
            jdbcTemplate.update(
                    "DELETE FROM audit_event_query_snapshots "
                            + "WHERE actor_user_id = ?",
                    auditorId
            );
        }
    }

    @Test
    @DisplayName("Should keep the result set stable while new events arrive")
    void shouldKeepResultSetStable() throws Exception {
        User auditor = createAuditor();
        String token = login(auditor);

        Instant baseTime = Instant.now().minusSeconds(120);
        persistEvents(auditor, baseTime, 3);

        ResultActions first = search(token, null, 0, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3));

        String snapshot = snapshotOf(first);
        assertNotNull(snapshot);

        // A matching event committed after the snapshot was created.
        persistEvents(auditor, baseTime.plusSeconds(30), 1);

        search(token, snapshot, 1, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(3));

        // Without the header a new snapshot is taken and sees the new event.
        ResultActions fresh = search(token, null, 1, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(4));

        assertNotEquals(snapshot, snapshotOf(fresh));
    }

    @Test
    @DisplayName("Should accept another page size with the same snapshot")
    void shouldAcceptAnotherPageSize() throws Exception {
        User auditor = createAuditor();
        String token = login(auditor);

        persistEvents(auditor, Instant.now().minusSeconds(120), 3);

        String snapshot = snapshotOf(
                search(token, null, 0, 2).andExpect(status().isOk())
        );

        search(token, snapshot, 0, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    @DisplayName("Should reject a snapshot used with different filters")
    void shouldRejectChangedFilters() throws Exception {
        User auditor = createAuditor();
        String token = login(auditor);

        persistEvents(auditor, Instant.now().minusSeconds(120), 2);

        String snapshot = snapshotOf(
                search(token, null, 0, 2).andExpect(status().isOk())
        );

        mockMvc.perform(get(AUDIT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(SNAPSHOT_HEADER, snapshot)
                        .param("entityType", "USER")
                        .param("entityId", entityId + "-other")
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject a snapshot that belongs to another auditor")
    void shouldRejectSnapshotOfAnotherAuditor() throws Exception {
        User owner = createAuditor();
        User other = createAuditor();

        String ownerToken = login(owner);
        String otherToken = login(other);

        persistEvents(owner, Instant.now().minusSeconds(120), 2);

        String snapshot = snapshotOf(
                search(ownerToken, null, 0, 2).andExpect(status().isOk())
        );

        search(otherToken, snapshot, 0, 2)
                .andExpect(status().isBadRequest());

        search(ownerToken, snapshot, 0, 2)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject blank and malformed snapshot headers")
    void shouldRejectBlankAndMalformedSnapshotHeaders() throws Exception {
        User auditor = createAuditor();
        String token = login(auditor);

        search(token, " ", 0, 2).andExpect(status().isBadRequest());
        search(token, "not-a-uuid", 0, 2).andExpect(status().isBadRequest());
    }

    private ResultActions search(
            String token,
            String snapshot,
            int page,
            int size
    ) throws Exception {
        var request = get(AUDIT_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("entityType", "USER")
                .param("entityId", entityId)
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .param("sort", "createdAt,asc");

        if (snapshot != null) {
            request.header(SNAPSHOT_HEADER, snapshot);
        }

        return mockMvc.perform(request);
    }

    private static String snapshotOf(ResultActions result) {
        return result.andReturn().getResponse().getHeader(SNAPSHOT_HEADER);
    }

    private User createAuditor() {
        String username = "e2e" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);

        String employeeId = "EMP-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        User auditor = userRepository.save(User.createHuman(
                employeeId,
                "End-to-End Test Auditor",
                username,
                username + "@test.com",
                passwordEncoder.encode(PASSWORD),
                Role.AUDITOR
        ));

        createdAuditorIds.add(auditor.getId());

        return auditor;
    }

    private String login(User user) throws Exception {
        String json = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "%s",
                                    "password": "%s"
                                }
                                """.formatted(user.getUsername(), PASSWORD))
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(json, "$.token");
    }

    private void persistEvents(User actor, Instant baseTime, int quantity) {
        for (int index = 0; index < quantity; index++) {
            auditEventRepository.save(AuditEvent.create(
                    AuditEventType.USER_CREATED,
                    AuditOutcome.SUCCESS,
                    AuditActorType.USER,
                    actor.getId(),
                    actor.getUsername(),
                    AuditEntityType.USER,
                    entityId,
                    UUID.randomUUID(),
                    baseTime.plusSeconds(index)
            ));
        }
    }
}
