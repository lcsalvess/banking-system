package com.lcsalvess.bankingsystem.integration.transaction;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.entity.*;
import com.lcsalvess.bankingsystem.entity.enums.AccountStatus;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.entity.enums.State;
import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import com.lcsalvess.bankingsystem.exception.account.AccountHasBalanceException;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.transaction.InsufficientBalanceException;
import com.lcsalvess.bankingsystem.repository.*;
import com.lcsalvess.bankingsystem.repository.specification.TransactionSpecifications;
import com.lcsalvess.bankingsystem.service.account.AccountNumberGenerator;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.account.GeneratedAccountNumber;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.transaction.DailyYieldProcessingResult;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class TransactionConcurrencyTests {

    // Keep at or below the Hikari default pool size (10).
    private static final int THREADS = 10;
    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountNumberGenerator accountNumberGenerator;

    @Autowired
    private AccountService accountService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    @MockitoBean
    private CurrentUserService currentUserService;

    private int clientSequence;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        clientRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();

        clientSequence = 0;

        User testUser = userRepository.saveAndFlush(
                new User(
                        "test-user",
                        "test-user@test.com",
                        "test-password",
                        Role.EMPLOYEE
                )
        );

        when(currentUserService.getCurrentUser()).thenReturn(testUser);
        when(currentUserService.getUsername()).thenReturn(testUser.getUsername());
    }

    // ---------------------------------------------------------------------
    // Concurrent withdrawals
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrent withdrawals")
    class ConcurrentWithdrawals {

        @Test
        @DisplayName("Should prevent two withdrawals from overdrawing the account")
        void shouldPreventConcurrentWithdrawalsFromOverdrawingAccount()
                throws Exception {

            CheckingAccount account = createCheckingAccount("1000.00");

            List<Boolean> results = runConcurrently(List.of(
                    withdrawal(account, "700.00"),
                    withdrawal(account, "700.00")
            ));

            assertEquals(1, countSucceeded(results));
            assertBalance("300.00", account);
            assertEquals(
                    1,
                    countTransactions(account, TransactionType.WITHDRAWAL)
            );
        }

        @Test
        @DisplayName("Should allow only as many withdrawals as the balance supports")
        void shouldAllowOnlyAsManyWithdrawalsAsBalanceSupports()
                throws Exception {

            CheckingAccount account = createCheckingAccount("1000.00");

            List<Callable<Boolean>> tasks =
                    repeat(THREADS, () -> withdrawal(account, "200.00"));

            List<Boolean> results = runConcurrently(tasks);

            assertEquals(5, countSucceeded(results));
            assertBalance("0.00", account);
            assertEquals(
                    5,
                    countTransactions(account, TransactionType.WITHDRAWAL)
            );
        }
    }

    // ---------------------------------------------------------------------
    // Concurrent deposits
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrent deposits")
    class ConcurrentDeposits {

        @Test
        @DisplayName("Should not lose any deposit")
        void shouldNotLoseAnyDeposit() throws Exception {
            CheckingAccount account = createCheckingAccount("0.00");

            List<Callable<Boolean>> tasks =
                    repeat(THREADS, () -> deposit(account, "50.00"));

            List<Boolean> results = runConcurrently(tasks);

            assertEquals(THREADS, countSucceeded(results));
            assertBalance("500.00", account);
            assertEquals(
                    THREADS,
                    countTransactions(account, TransactionType.DEPOSIT)
            );
        }

        @Test
        @DisplayName("Should keep balance consistent with mixed deposits and withdrawals")
        void shouldKeepBalanceConsistentWithMixedOperations() throws Exception {
            CheckingAccount account = createCheckingAccount("1000.00");

            List<Callable<Boolean>> tasks = new ArrayList<>();
            tasks.addAll(
                    repeat(THREADS / 2, () -> deposit(account, "200.00"))
            );
            tasks.addAll(
                    repeat(THREADS / 2, () -> withdrawal(account, "200.00"))
            );

            List<Boolean> results = runConcurrently(tasks);

            assertEquals(THREADS, countSucceeded(results));
            assertBalance("1000.00", account);
            assertEquals(
                    THREADS / 2,
                    countTransactions(account, TransactionType.DEPOSIT)
            );
            assertEquals(
                    THREADS / 2,
                    countTransactions(account, TransactionType.WITHDRAWAL)
            );
        }
    }

    // ---------------------------------------------------------------------
    // Concurrent transfers
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrent transfers")
    class ConcurrentTransfers {

        @Test
        @DisplayName("Should complete concurrent opposite transfers without deadlock")
        void shouldCompleteConcurrentOppositeTransfersWithoutDeadlock()
                throws Exception {

            CheckingAccount first = createCheckingAccount("1000.00");
            CheckingAccount second = createCheckingAccount("1000.00");

            List<Boolean> results = runConcurrently(List.of(
                    transfer(first, second, "100.00"),
                    transfer(second, first, "100.00")
            ));

            assertEquals(2, countSucceeded(results));

            assertBalance("1000.00", first);
            assertBalance("1000.00", second);

            assertEquals(
                    1,
                    countTransactions(first, TransactionType.TRANSFER_SENT)
            );
            assertEquals(
                    1,
                    countTransactions(first, TransactionType.TRANSFER_RECEIVED)
            );
            assertEquals(
                    1,
                    countTransactions(second, TransactionType.TRANSFER_SENT)
            );
            assertEquals(
                    1,
                    countTransactions(second, TransactionType.TRANSFER_RECEIVED)
            );
        }

        @Test
        @DisplayName("Should not lose credits when multiple transfers target the same account")
        void shouldNotLoseCreditsWhenMultipleTransfersTargetSameAccount()
                throws Exception {

            CheckingAccount firstSource = createCheckingAccount("100.00");
            CheckingAccount secondSource = createCheckingAccount("100.00");
            CheckingAccount thirdSource = createCheckingAccount("100.00");
            CheckingAccount destination = createCheckingAccount("0.00");

            List<Boolean> results = runConcurrently(List.of(
                    transfer(firstSource, destination, "50.00"),
                    transfer(secondSource, destination, "50.00"),
                    transfer(thirdSource, destination, "50.00")
            ));

            assertEquals(3, countSucceeded(results));

            assertBalance("50.00", firstSource);
            assertBalance("50.00", secondSource);
            assertBalance("50.00", thirdSource);
            assertBalance("150.00", destination);

            assertEquals(
                    3,
                    countTransactions(destination, TransactionType.TRANSFER_RECEIVED)
            );
        }

        @Test
        @DisplayName("Should allow only as many concurrent transfers as the balance supports")
        void shouldAllowOnlyAsManyConcurrentTransfersAsBalanceSupports()
                throws Exception {

            CheckingAccount source = createCheckingAccount("100.00");
            CheckingAccount destination = createCheckingAccount("0.00");

            List<Callable<Boolean>> tasks =
                    repeat(THREADS, () -> transfer(source, destination, "20.00"));

            List<Boolean> results = runConcurrently(tasks);

            assertEquals(5, countSucceeded(results));

            assertBalance("0.00", source);
            assertBalance("100.00", destination);

            assertEquals(
                    5,
                    countTransactions(source, TransactionType.TRANSFER_SENT)
            );
            assertEquals(
                    5,
                    countTransactions(destination, TransactionType.TRANSFER_RECEIVED)
            );
        }
    }

// ---------------------------------------------------------------------
// Concurrent daily yield processing
// ---------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrent daily yield processing")
    class ConcurrentDailyYieldProcessing {

        @Test
        @DisplayName("Should apply daily yield only once when requests are concurrent")
        void shouldApplyDailyYieldOnlyOnceWhenRequestsAreConcurrent()
                throws Exception {

            SavingsAccount account = createSavingsAccount("1000.00");
            LocalDate referenceDate = LocalDate.now(clock);

            transactionRepository.saveAndFlush(
                    new Transaction(
                            TransactionType.DEPOSIT,
                            new BigDecimal("1000.00"),
                            referenceDate.minusDays(1).atTime(12, 0),
                            account,
                            findTestUser()
                    )
            );

            List<Boolean> results = runConcurrently(List.of(
                    processDailyYield(account, referenceDate),
                    processDailyYield(account, referenceDate)
            ));

            assertEquals(1, countSucceeded(results));
            assertBalance("1000.17", account);

            assertEquals(
                    1,
                    countTransactions(account, TransactionType.YIELD)
            );

            SavingsAccount persisted = (SavingsAccount)
                    accountRepository.findById(account.getId())
                            .orElseThrow();

            assertEquals(referenceDate, persisted.getLastYieldDate());
            assertEquals(
                    referenceDate,
                    persisted.getLastYieldProcessedDate()
            );

            List<Transaction> yieldTransactions = transactionRepository.findAll(
                    TransactionSpecifications.byAccountAndFilters(
                            account.getId(),
                            TransactionType.YIELD,
                            null,
                            null
                    )
            );

            assertEquals(1, yieldTransactions.size());

            Transaction yieldTransaction = yieldTransactions.getFirst();

            assertEquals(referenceDate, yieldTransaction.getYieldDate());
            assertNull(yieldTransaction.getPerformedBy());
            assertTrue(yieldTransaction.isPerformedBySystem());
        }
    }

    // ---------------------------------------------------------------------
    // Concurrent account cancellation and deposits
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrent account cancellation and deposits")
    class ConcurrentCancellationAndDeposits {

        @Test
        @DisplayName("Should preserve account state when cancellation races with deposit")
        void shouldPreserveAccountStateWhenCancellationRacesWithDeposit()
                throws Exception {

            CheckingAccount account = createCheckingAccount("0.00");

            List<Boolean> results = runConcurrently(List.of(
                    deposit(account, "100.00"),
                    cancel(account)
            ));

            assertEquals(1, countSucceeded(results));

            Account persisted = accountRepository.findById(account.getId())
                    .orElseThrow();

            if (persisted.getStatus() == AccountStatus.CANCELLED) {
                assertEquals(
                        0,
                        BigDecimal.ZERO.compareTo(persisted.getBalance())
                );
                assertEquals(
                        0,
                        countTransactions(account, TransactionType.DEPOSIT)
                );
            } else {
                assertEquals(
                        0,
                        new BigDecimal("100.00").compareTo(persisted.getBalance())
                );
                assertEquals(
                        1,
                        countTransactions(account, TransactionType.DEPOSIT)
                );
            }
        }
    }

    // ---------------------------------------------------------------------
    // Historical balance reconstruction
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Historical balance reconstruction")
    class HistoricalBalanceReconstruction {

        @Test
        @DisplayName("Should reconstruct the balance at the start of a day")
        void shouldCalculateBalanceAtStartOfDay() {
            SavingsAccount account = createSavingsAccount("0.00");
            User actor = findTestUser();

            LocalDate referenceDate = LocalDate.of(2026, 1, 16);

            UUID outgoingTransferCode = UUID.randomUUID();
            UUID incomingTransferCode = UUID.randomUUID();

            transactionRepository.saveAllAndFlush(List.of(
                    new Transaction(
                            TransactionType.DEPOSIT,
                            new BigDecimal("100.00"),
                            LocalDateTime.of(2026, 1, 10, 10, 0),
                            account,
                            actor
                    ),
                    new Transaction(
                            TransactionType.WITHDRAWAL,
                            new BigDecimal("10.00"),
                            LocalDateTime.of(2026, 1, 11, 10, 0),
                            account,
                            actor
                    ),
                    new Transaction(
                            TransactionType.TRANSFER_SENT,
                            new BigDecimal("20.00"),
                            LocalDateTime.of(2026, 1, 12, 10, 0),
                            account,
                            outgoingTransferCode,
                            actor
                    ),
                    new Transaction(
                            TransactionType.TRANSFER_RECEIVED,
                            new BigDecimal("30.00"),
                            LocalDateTime.of(2026, 1, 13, 10, 0),
                            account,
                            incomingTransferCode,
                            actor
                    ),
                    new Transaction(
                            TransactionType.YIELD,
                            new BigDecimal("2.50"),
                            LocalDateTime.of(2026, 1, 16, 0, 5),
                            LocalDate.of(2026, 1, 15),
                            account
                    ),
                    new Transaction(
                            TransactionType.DEPOSIT,
                            new BigDecimal("999.00"),
                            LocalDateTime.of(2026, 1, 16, 10, 0),
                            account,
                            actor
                    )
            ));

            BigDecimal actual =
                    transactionRepository.calculateBalanceAtStartOfDay(
                            account.getId(),
                            referenceDate,
                            referenceDate.atStartOfDay()
                    );

            assertEquals(
                    0,
                    new BigDecimal("102.50").compareTo(actual),
                    "The historical balance should include only transactions "
                            + "effective before the reference day."
            );
        }
    }

    // ---------------------------------------------------------------------
    // Sequential daily yield recovery
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Sequential daily yield recovery")
    class SequentialDailyYieldRecovery {

        @Test
        @DisplayName("Should recover pending dates and compound daily yields correctly")
        void shouldRecoverPendingDatesAndCompoundYieldsCorrectly() {
            SavingsAccount account = createSavingsAccount("1000.00");

            LocalDate today = LocalDate.now(clock);
            LocalDate lastProcessedDate = today.minusDays(3);

            setYieldProcessingDates(
                    account,
                    lastProcessedDate,
                    lastProcessedDate
            );

            SavingsAccount persistedAccount = (SavingsAccount)
                    accountRepository.findById(account.getId())
                            .orElseThrow();

            transactionRepository.saveAndFlush(
                    new Transaction(
                            TransactionType.DEPOSIT,
                            new BigDecimal("1000.00"),
                            lastProcessedDate.minusDays(1).atTime(12, 0),
                            persistedAccount,
                            findTestUser()
                    )
            );

            // Process the first pending date.
            LocalDate firstDate = lastProcessedDate.plusDays(1);

            DailyYieldProcessingResult firstResult =
                    transactionService.processDailyYieldForDate(
                            account.getAccountNumber(),
                            account.getDigit(),
                            firstDate
                    );

            assertEquals(
                    DailyYieldProcessingResult.APPLIED,
                    firstResult
            );

            // Simulate a restart by clearing the persistence context.
            entityManager.clear();

            SavingsAccount checkpointedAccount = (SavingsAccount)
                    accountRepository.findById(account.getId())
                            .orElseThrow();

            assertEquals(
                    firstDate,
                    checkpointedAccount.getLastYieldProcessedDate(),
                    "The first processed date must survive a persistence-context reset."
            );

            // Resume from the next pending date.
            for (
                    LocalDate referenceDate = firstDate.plusDays(1);
                    !referenceDate.isAfter(today);
                    referenceDate = referenceDate.plusDays(1)
            ) {
                DailyYieldProcessingResult result =
                        transactionService.processDailyYieldForDate(
                                account.getAccountNumber(),
                                account.getDigit(),
                                referenceDate
                        );

                assertEquals(
                        DailyYieldProcessingResult.APPLIED,
                        result,
                        "Expected recovery to resume successfully for " + referenceDate
                );
            }

            assertBalance("1000.51", account);

            assertEquals(
                    3,
                    countTransactions(account, TransactionType.YIELD)
            );

            SavingsAccount recoveredAccount = (SavingsAccount)
                    accountRepository.findById(account.getId())
                            .orElseThrow();

            assertEquals(today, recoveredAccount.getLastYieldDate());
            assertEquals(
                    today,
                    recoveredAccount.getLastYieldProcessedDate()
            );
        }
    }

    // ---------------------------------------------------------------------
    // Tasks
    // ---------------------------------------------------------------------

    private Callable<Boolean> deposit(Account account, String amount) {
        return () -> {
            try {
                transactionService.deposit(operation(account, amount));
                return true;
            } catch (AccountIsNotActiveException ex) {
                return false;
            }
        };
    }

    private Callable<Boolean> withdrawal(Account account, String amount) {
        return () -> {
            try {
                transactionService.withdraw(operation(account, amount));
                return true;
            } catch (InsufficientBalanceException ex) {
                return false;
            }
        };
    }

    private Callable<Boolean> transfer(
            Account source,
            Account destination,
            String amount
    ) {
        return () -> {
            try {
                transactionService.transfer(
                        new TransferRequestDTO(
                                source.getAccountNumber(),
                                source.getDigit(),
                                destination.getAccountNumber(),
                                destination.getDigit(),
                                new BigDecimal(amount)
                        )
                );

                return true;
            } catch (InsufficientBalanceException ex) {
                return false;
            }
        };
    }

    private Callable<Boolean> processDailyYield(
            SavingsAccount account,
            LocalDate referenceDate
    ) {
        return () -> transactionService.processDailyYieldForDate(
                account.getAccountNumber(),
                account.getDigit(),
                referenceDate
        ) == DailyYieldProcessingResult.APPLIED;
    }

    private Callable<Boolean> cancel(Account account) {
        return () -> {
            try {
                accountService.cancel(
                        account.getAccountNumber(),
                        account.getDigit()
                );

                return true;
            } catch (AccountHasBalanceException ex) {
                return false;
            }
        };
    }

    private AccountOperationRequestDTO operation(Account account, String amount) {
        return new AccountOperationRequestDTO(
                account.getAccountNumber(),
                account.getDigit(),
                new BigDecimal(amount)
        );
    }

    // ---------------------------------------------------------------------
    // Concurrency helpers
    // ---------------------------------------------------------------------

    /**
     * Starts all tasks together. Unexpected failures are rethrown through Future.get(),
     * so deadlocks and lock timeouts fail the test rather than being silently ignored.
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
                    "Threads did not become ready in time."
            );

            start.countDown();

            List<Boolean> results = new ArrayList<>();

            for (Future<Boolean> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            return results;

        } finally {
            executor.shutdownNow();
        }
    }

    private List<Callable<Boolean>> repeat(
            int times,
            Supplier<Callable<Boolean>> factory
    ) {
        return IntStream.range(0, times)
                .mapToObj(i -> factory.get())
                .toList();
    }

    private long countSucceeded(List<Boolean> results) {
        return results.stream()
                .filter(Boolean::booleanValue)
                .count();
    }

    // ---------------------------------------------------------------------
    // Fixtures and assertions
    // ---------------------------------------------------------------------

    private CheckingAccount createCheckingAccount(String initialBalance) {
        Client client = createClient();
        GeneratedAccountNumber generated = accountNumberGenerator.generate();

        CheckingAccount account = new CheckingAccount(
                client,
                generated.number(),
                generated.digit()
        );

        account.credit(new BigDecimal(initialBalance));

        return accountRepository.saveAndFlush(account);
    }

    private SavingsAccount createSavingsAccount(String initialBalance) {
        Client client = createClient();
        GeneratedAccountNumber generated = accountNumberGenerator.generate();

        // Both yield dates begin yesterday, making the account eligible today.
        LocalDate initialYieldDate = LocalDate.now(clock).minusDays(1);

        SavingsAccount account = new SavingsAccount(
                client,
                generated.number(),
                generated.digit(),
                initialYieldDate
        );

        account.credit(new BigDecimal(initialBalance));

        return accountRepository.saveAndFlush(account);
    }

    /**
     * Overrides the persisted checkpoint only when a test needs a historical scenario.
     * The persistence context is cleared to prevent stale entity state after direct SQL.
     */
    private void setYieldProcessingDates(
            SavingsAccount account,
            LocalDate lastYieldDate,
            LocalDate lastProcessedDate
    ) {
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE savings_accounts
                SET last_yield_date = ?,
                    last_yield_processed_date = ?
                WHERE id = ?
                """,
                lastYieldDate,
                lastProcessedDate,
                account.getId()
        );

        assertEquals(
                1,
                updatedRows,
                "Exactly one savings account should be updated."
        );

        entityManager.clear();
    }

    private User findTestUser() {
        return userRepository
                .findByUsernameIgnoreCase("test-user")
                .orElseThrow();
    }

    private Client createClient() {
        int n = ++clientSequence;

        Address address = new Address(
                "Rua Teste",
                String.valueOf(n),
                null,
                "Centro",
                "Mogi das Cruzes",
                State.SP,
                "08700000"
        );

        Client client = new Client(
                "Cliente Concorrencia " + n,
                String.format("%011d", n),
                "concurrency" + n + "@test.com",
                "11999999999",
                address
        );

        return clientRepository.saveAndFlush(client);
    }

    private void assertBalance(String expected, Account account) {
        BigDecimal actual = accountRepository.findById(account.getId())
                .orElseThrow()
                .getBalance();

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual),
                "Expected balance " + expected + " but was " + actual
        );
    }

    private long countTransactions(Account account, TransactionType type) {
        return transactionRepository.count(
                TransactionSpecifications.byAccountAndFilters(
                        account.getId(),
                        type,
                        null,
                        null
                )
        );
    }
}