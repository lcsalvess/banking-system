package com.lcsalvess.bankingsystem.integration.user;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.user.SelfRoleChangeNotAllowedException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.user.UserService;
import com.lcsalvess.bankingsystem.testsupport.LedgerTestCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest
class UserRoleChangeConcurrencyTests {

    private static final int TIMEOUT_SECONDS = 30;

    private static final String REPETITION_NAME =
            "{displayName} [{currentRepetition}/{totalRepetitions}]";

    private static final String EMPLOYEE_ID_PREFIX = "EMP-TEST-";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private LedgerTestCleaner ledgerTestCleaner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        ledgerTestCleaner.deleteAllTransactions();
        userRepository.deleteAll();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Concurrent role changes by administrators")
    class ConcurrentRoleChanges {

        @RepeatedTest(value = 20, name = REPETITION_NAME)
        @DisplayName("Should keep one active administrator when two administrators demote each other")
        void shouldKeepOneAdminWhenTwoAdminsDemoteEachOther()
                throws Exception {

            List<User> admins = createAdmins(2);

            User first = admins.get(0);
            User second = admins.get(1);

            List<Boolean> results = runConcurrently(List.of(
                    roleChange(first, second, Role.EMPLOYEE),
                    roleChange(second, first, Role.EMPLOYEE)
            ));

            // The caller that loses the race is no longer an active administrator
            // when it acquires the lock, so the operation is denied.
            assertEquals(1L, countSucceeded(results));
            assertEquals(1L, countActiveAdmins());
        }

        @RepeatedTest(value = 20, name = REPETITION_NAME)
        @DisplayName("Should never leave the system without an administrator in a demotion chain")
        void shouldNeverLeaveSystemWithoutAdminInDemotionChain()
                throws Exception {

            List<User> admins = createAdmins(3);

            User a = admins.get(0);
            User b = admins.get(1);
            User c = admins.get(2);

            // A demotes B while B attempts to demote C.
            List<Boolean> results = runConcurrently(List.of(
                    roleChange(a, b, Role.EMPLOYEE),
                    roleChange(b, c, Role.EMPLOYEE)
            ));

            long succeeded = countSucceeded(results);

            assertTrue(succeeded >= 1L);
            assertTrue(countActiveAdmins() >= 1L);
            assertEquals(3L - succeeded, countActiveAdmins());
        }
    }

    @Nested
    @DisplayName("Sequential rules")
    class SequentialRules {

        @Test
        @DisplayName("Should reject an administrator changing their own role")
        void shouldRejectSelfRoleChange() {

            User admin = createAdmins(1).getFirst();

            authenticateAs(admin);

            assertThrows(
                    SelfRoleChangeNotAllowedException.class,
                    () -> userService.changeRole(
                            admin.getEmployeeId(),
                            Role.EMPLOYEE
                    )
            );

            assertEquals(Role.ADMIN, roleOf(admin));
            assertEquals(0, tokenVersionOf(admin));
        }

        @Test
        @DisplayName("Should change the role, revoke tokens and audit the previous and new role")
        void shouldChangeRoleRevokeTokensAndAudit() {

            User admin = createAdmins(1).getFirst();

            User employee = createUser(
                    "employee-role-change",
                    Role.EMPLOYEE
            );

            authenticateAs(admin);

            userService.changeRole(
                    employee.getEmployeeId(),
                    Role.AUDITOR
            );

            assertEquals(Role.AUDITOR, roleOf(employee));
            assertEquals(1, tokenVersionOf(employee));

            List<Map<String, Object>> events = jdbcTemplate.queryForList(
                    "SELECT reason, actor_user_id, outcome FROM audit_events "
                            + "WHERE event_type = 'USER_ROLE_CHANGED' AND entity_id = ?",
                    String.valueOf(employee.getId())
            );

            assertEquals(1, events.size());
            assertEquals("EMPLOYEE_TO_AUDITOR", events.getFirst().get("reason"));
            assertEquals("SUCCESS", events.getFirst().get("outcome"));
            assertEquals(
                    admin.getId(),
                    ((Number) events.getFirst().get("actor_user_id")).longValue()
            );
        }

        @Test
        @DisplayName("Should deny when the caller is no longer an active administrator")
        void shouldDenyWhenCallerIsNoLongerAdmin() {

            User admin = createAdmins(1).getFirst();

            User employee = createUser(
                    "employee-role-change",
                    Role.EMPLOYEE
            );

            // The caller is demoted after authenticating the request.
            User stale = userRepository.findById(admin.getId()).orElseThrow();

            stale.changeRole(Role.EMPLOYEE);

            userRepository.saveAndFlush(stale);

            authenticateAs(admin);

            assertThrows(
                    AccessDeniedException.class,
                    () -> userService.changeRole(
                            employee.getEmployeeId(),
                            Role.ADMIN
                    )
            );

            assertEquals(Role.EMPLOYEE, roleOf(employee));
        }
    }

    // ---------------------------------------------------------------------
    // Tasks
    // ---------------------------------------------------------------------

    /**
     * Executes a role change as the requesting administrator.
     * Each thread has its own SecurityContext, as in a real request.
     * Only denial caused by losing the administrator role is expected.
     * Any other exception, lock timeout or deadlock fails the test.
     */
    private Callable<Boolean> roleChange(
            User caller,
            User target,
            Role newRole
    ) {
        return () -> {
            authenticateAs(caller);

            try {
                userService.changeRole(
                        target.getEmployeeId(),
                        newRole
                );

                return true;

            } catch (AccessDeniedException exception) {
                return false;

            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities()
                )
        );
    }

    // ---------------------------------------------------------------------
    // Concurrency helpers
    // ---------------------------------------------------------------------

    private List<Boolean> runConcurrently(
            List<Callable<Boolean>> tasks
    ) throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(tasks.size());

        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return task.call();
                    }))
                    .toList();

            assertTrue(
                    ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Threads did not become ready in time"
            );

            start.countDown();

            List<Boolean> results = new ArrayList<>();

            for (Future<Boolean> future : futures) {
                results.add(
                        future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                );
            }

            return results;

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private long countSucceeded(List<Boolean> results) {
        return results.stream()
                .filter(Boolean::booleanValue)
                .count();
    }

    // ---------------------------------------------------------------------
    // Fixtures and assertions
    // ---------------------------------------------------------------------

    private List<User> createAdmins(int quantity) {
        return IntStream.rangeClosed(1, quantity)
                .mapToObj(number ->
                        createUser(
                                "admin-role-" + number,
                                Role.ADMIN
                        )
                )
                .toList();
    }

    private User createUser(String username, Role role) {
        String employeeId = EMPLOYEE_ID_PREFIX
                + username.toUpperCase(Locale.ROOT);

        User user = User.createHuman(
                employeeId,
                "Concurrency Test User",
                username,
                username + "@test.com",
                "test-password",
                role
        );

        return userRepository.saveAndFlush(user);
    }

    private Role roleOf(User user) {
        return userRepository.findById(user.getId())
                .orElseThrow()
                .getRole();
    }

    private int tokenVersionOf(User user) {
        return userRepository.findById(user.getId())
                .orElseThrow()
                .getTokenVersion();
    }

    private long countActiveAdmins() {
        return userRepository.findAll()
                .stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .filter(User::isActive)
                .count();
    }
}
