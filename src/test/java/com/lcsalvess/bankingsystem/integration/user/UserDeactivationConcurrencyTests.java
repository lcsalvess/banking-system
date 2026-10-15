package com.lcsalvess.bankingsystem.integration.user;

import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.exception.user.LastAdminCannotBeDeactivatedException;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.user.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
class UserDeactivationConcurrencyTests {

    private static final int TIMEOUT_SECONDS = 30;

    // A janela da race é pequena: repetir faz o teste falhar de forma
    // confiável se o lock for removido, em vez de passar por sorte.
    private static final String REPETITION_NAME =
            "{displayName} [{currentRepetition}/{totalRepetitions}]";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAllInBatch();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("Concurrent administrator deactivation")
    class ConcurrentAdminDeactivation {

        @RepeatedTest(value = 20, name = REPETITION_NAME)
        @DisplayName("Should preserve one active administrator when two are deactivated concurrently")
        void shouldPreserveOneActiveAdminWhenTwoAreDeactivatedConcurrently()
                throws Exception {

            List<User> admins = createAdmins(2);

            List<Boolean> results = runConcurrently(
                    admins.stream()
                            .map(UserDeactivationConcurrencyTests.this::deactivation)
                            .toList()
            );

            assertEquals(1L, countSucceeded(results));

            assertEquals(1L, countActiveAdmins());
        }

        @RepeatedTest(value = 20, name = REPETITION_NAME)
        @DisplayName("Should preserve one active administrator when three are deactivated concurrently")
        void shouldPreserveOneActiveAdminWhenThreeAreDeactivatedConcurrently()
                throws Exception {

            List<User> admins = createAdmins(3);

            List<Boolean> results = runConcurrently(
                    admins.stream()
                            .map(UserDeactivationConcurrencyTests.this::deactivation)
                            .toList()
            );

            assertEquals(2L, countSucceeded(results));

            assertEquals(1L, countActiveAdmins());
        }

        @RepeatedTest(value = 20, name = REPETITION_NAME)
        @DisplayName("Should be idempotent when the same administrator is deactivated concurrently")
        void shouldBeIdempotentWhenSameAdminIsDeactivatedConcurrently()
                throws Exception {

            List<User> admins = createAdmins(2);
            User target = admins.get(0);
            User other = admins.get(1);

            List<Boolean> results = runConcurrently(List.of(
                    deactivation(target),
                    deactivation(target)
            ));

            // Ambas as chamadas são bem-sucedidas: a segunda vê o alvo já
            // desativado e retorna sem lançar 409.
            assertEquals(2L, countSucceeded(results));

            assertFalse(isActive(target));
            assertTrue(isActive(other));
            assertEquals(1L, countActiveAdmins());
        }
    }

    @Nested
    @DisplayName("Non-administrator deactivation")
    class NonAdminDeactivation {

        @Test
        @DisplayName("Should deactivate an employee even when only one administrator exists")
        void shouldDeactivateEmployeeWhenOnlyOneAdminExists() {
            createAdmins(1);
            User employee = createUser("employee-concurrency", Role.EMPLOYEE);

            userService.deactivate(employee.getId());

            assertFalse(isActive(employee));
            assertEquals(1L, countActiveAdmins());
        }
    }

    // ---------------------------------------------------------------------
    // Tasks
    // ---------------------------------------------------------------------

    private Callable<Boolean> deactivation(User user) {
        return () -> {
            try {
                userService.deactivate(user.getId());
                return true;
            } catch (LastAdminCannotBeDeactivatedException ex) {
                return false;
            }
        };
    }

    // ---------------------------------------------------------------------
    // Concurrency helpers
    // ---------------------------------------------------------------------

    /**
     * Starts all tasks together using a readiness barrier and a start latch.
     * Unexpected exceptions, lock timeouts, and deadlocks fail the test.
     */
    private List<Boolean> runConcurrently(List<Callable<Boolean>> tasks)
            throws Exception {

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
                        createUser("admin-concurrency-" + number, Role.ADMIN))
                .toList();
    }

    private User createUser(String username, Role role) {
        User user = new User(
                username,
                username + "@test.com",
                "test-password",
                role
        );

        return userRepository.saveAndFlush(user);
    }

    private boolean isActive(User user) {
        return userRepository.findById(user.getId())
                .orElseThrow()
                .isActive();
    }

    private long countActiveAdmins() {
        return userRepository.findAll()
                .stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .filter(User::isActive)
                .count();
    }
}