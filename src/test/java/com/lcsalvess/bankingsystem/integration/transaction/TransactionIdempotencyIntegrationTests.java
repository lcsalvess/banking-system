package com.lcsalvess.bankingsystem.integration.transaction;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.address.Address;
import com.lcsalvess.bankingsystem.entity.address.State;
import com.lcsalvess.bankingsystem.entity.client.Client;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.transaction.IdempotencyKeyConflictException;
import com.lcsalvess.bankingsystem.exception.transaction.InsufficientBalanceException;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.repository.AddressRepository;
import com.lcsalvess.bankingsystem.repository.ClientRepository;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.repository.idempotency.TransactionIdempotencyRepository;
import com.lcsalvess.bankingsystem.service.account.AccountNumberGenerator;
import com.lcsalvess.bankingsystem.service.account.GeneratedAccountNumber;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionIdempotencyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
@DisplayName("Transaction idempotency integration")
class TransactionIdempotencyIntegrationTests {

    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private TransactionIdempotencyService transactionIdempotencyService;

    @Autowired
    private TransactionIdempotencyRepository idempotencyRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountNumberGenerator accountNumberGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private CurrentUserService currentUserService;

    private int clientSequence;
    private User testUser;

    @BeforeEach
    void setUp() {
        clearIdempotencyRequests();

        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        clientRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();

        clientSequence = 0;

        testUser = userRepository.saveAndFlush(
                new User(
                        "idempotency-user",
                        "idempotency-user@test.com",
                        "test-password",
                        Role.EMPLOYEE
                )
        );

        when(currentUserService.getCurrentUser()).thenReturn(testUser);
        when(currentUserService.getUsername()).thenReturn(testUser.getUsername());
    }

    @AfterEach
    void cleanUpIdempotencyRequests() {
        clearIdempotencyRequests();
    }

    @Nested
    @DisplayName("Repeated requests")
    class RepeatedRequests {

        @Test
        @DisplayName("Should return the original result without executing a second deposit")
        void shouldReturnOriginalResultWhenDepositIsRepeated() {
            CheckingAccount account = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            AccountOperationRequestDTO firstRequest =
                    operation(account, "100.00");

            AccountOperationRequestDTO retryRequest =
                    operation(account, "100.00");

            TransactionResponseDTO firstResponse =
                    transactionIdempotencyService.deposit(
                            idempotencyKey,
                            firstRequest
                    );

            TransactionResponseDTO retryResponse =
                    transactionIdempotencyService.deposit(
                            idempotencyKey,
                            retryRequest
                    );

            assertSameTransaction(firstResponse, retryResponse);

            assertEquals(
                    firstResponse.transactionCode(),
                    idempotencyRepository
                            .findByUserIdAndKey(testUser.getId(), idempotencyKey)
                            .orElseThrow()
                            .transactionCode()
            );

            assertBalance("100.00", account);
            assertEquals(1L, countTransactions(account));
            assertEquals(1L, countIdempotencyRequests());
        }

        @Test
        @DisplayName("Should treat equivalent amount scales as the same request")
        void shouldReplayWhenAmountScaleDiffers() {
            CheckingAccount account = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            TransactionResponseDTO firstResponse =
                    transactionIdempotencyService.deposit(
                            idempotencyKey,
                            operation(account, "100")
                    );

            TransactionResponseDTO retryResponse =
                    transactionIdempotencyService.deposit(
                            idempotencyKey,
                            operation(account, "100.00")
                    );

            assertSameTransaction(firstResponse, retryResponse);
            assertBalance("100.00", account);
            assertEquals(1L, countTransactions(account));
            assertEquals(1L, countIdempotencyRequests());
        }

        @Test
        @DisplayName("Should return the original result without executing a second transfer")
        void shouldReturnOriginalResultWhenTransferIsRepeated() {
            CheckingAccount source = createCheckingAccount("500.00");
            CheckingAccount destination = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            TransferRequestDTO firstRequest =
                    transfer(source, destination, "100.00");

            TransferRequestDTO retryRequest =
                    transfer(source, destination, "100.00");

            TransactionResponseDTO firstResponse =
                    transactionIdempotencyService.transfer(
                            idempotencyKey,
                            firstRequest
                    );

            TransactionResponseDTO retryResponse =
                    transactionIdempotencyService.transfer(
                            idempotencyKey,
                            retryRequest
                    );

            assertSameTransaction(firstResponse, retryResponse);

            assertNotNull(firstResponse.transferCode());

            assertBalance("400.00", source);
            assertBalance("100.00", destination);

            assertEquals(1L, countTransactions(source));
            assertEquals(1L, countTransactions(destination));
            assertEquals(1L, countIdempotencyRequests());
        }

        @Test
        @DisplayName("Should scope idempotency keys per user")
        void shouldScopeIdempotencyKeysPerUser() {
            CheckingAccount account = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            User otherUser = userRepository.saveAndFlush(
                    new User(
                            "idempotency-user-2",
                            "idempotency-user-2@test.com",
                            "test-password",
                            Role.EMPLOYEE
                    )
            );

            TransactionResponseDTO firstResponse =
                    transactionIdempotencyService.deposit(
                            idempotencyKey,
                            operation(account, "100.00")
                    );

            when(currentUserService.getCurrentUser()).thenReturn(otherUser);
            when(currentUserService.getUsername()).thenReturn(otherUser.getUsername());

            TransactionResponseDTO secondResponse =
                    transactionIdempotencyService.deposit(
                            idempotencyKey,
                            operation(account, "100.00")
                    );

            assertNotEquals(
                    firstResponse.transactionCode(),
                    secondResponse.transactionCode()
            );

            assertBalance("200.00", account);
            assertEquals(2L, countTransactions(account));
            assertEquals(2L, countIdempotencyRequests());
        }
    }

    @Nested
    @DisplayName("Idempotency key conflicts")
    class IdempotencyKeyConflicts {

        @Test
        @DisplayName("Should reject the same key when the deposit amount is different")
        void shouldRejectDifferentPayloadForExistingKey() {
            CheckingAccount account = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            transactionIdempotencyService.deposit(
                    idempotencyKey,
                    operation(account, "100.00")
            );

            assertThrows(
                    IdempotencyKeyConflictException.class,
                    () -> transactionIdempotencyService.deposit(
                            idempotencyKey,
                            operation(account, "200.00")
                    )
            );

            assertBalance("100.00", account);
            assertEquals(1L, countTransactions(account));
            assertEquals(1L, countIdempotencyRequests());
        }

        @Test
        @DisplayName("Should reject the same key when the target account is different")
        void shouldRejectDifferentAccountForExistingKey() {
            CheckingAccount firstAccount = createCheckingAccount("0.00");
            CheckingAccount secondAccount = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            transactionIdempotencyService.deposit(
                    idempotencyKey,
                    operation(firstAccount, "100.00")
            );

            assertThrows(
                    IdempotencyKeyConflictException.class,
                    () -> transactionIdempotencyService.deposit(
                            idempotencyKey,
                            operation(secondAccount, "100.00")
                    )
            );

            assertBalance("100.00", firstAccount);
            assertBalance("0.00", secondAccount);
            assertEquals(0L, countTransactions(secondAccount));
            assertEquals(1L, countIdempotencyRequests());
        }

        @Test
        @DisplayName("Should reject the same key when the operation type is different")
        void shouldRejectDifferentOperationForExistingKey() {
            CheckingAccount account = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            transactionIdempotencyService.deposit(
                    idempotencyKey,
                    operation(account, "100.00")
            );

            assertThrows(
                    IdempotencyKeyConflictException.class,
                    () -> transactionIdempotencyService.withdraw(
                            idempotencyKey,
                            operation(account, "100.00")
                    )
            );

            assertBalance("100.00", account);
            assertEquals(1L, countTransactions(account));
            assertEquals(1L, countIdempotencyRequests());
        }
    }

    @Nested
    @DisplayName("Failed operations")
    class FailedOperations {

        @Test
        @DisplayName("Should release the idempotency key when the financial operation rolls back")
        void shouldAllowRetryAfterFailedOperation() {
            CheckingAccount account = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            AccountOperationRequestDTO withdrawalRequest =
                    operation(account, "100.00");

            assertThrows(
                    InsufficientBalanceException.class,
                    () -> transactionIdempotencyService.withdraw(
                            idempotencyKey,
                            withdrawalRequest
                    )
            );

            assertEquals(
                    0L,
                    countIdempotencyRequests(),
                    "The failed transaction must not leave a reserved key."
            );

            transactionService.deposit(
                    operation(account, "200.00")
            );

            TransactionResponseDTO response =
                    transactionIdempotencyService.withdraw(
                            idempotencyKey,
                            withdrawalRequest
                    );

            assertEquals(TransactionType.WITHDRAWAL, response.type());
            assertMoneyEquals(new BigDecimal("100.00"), response.balanceAfter());

            assertBalance("100.00", account);
            assertEquals(2L, countTransactions(account));
            assertEquals(1L, countIdempotencyRequests());
        }
    }

    @Nested
    @DisplayName("Concurrent requests")
    class ConcurrentRequests {

        @Test
        @DisplayName("Should block while the key is reserved by an uncommitted transaction")
        void shouldBlockUntilReservingTransactionEnds() throws Exception {
            CheckingAccount account = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<TransactionResponseDTO> blockedCall;

            TransactionStatus reservingTransaction =
                    transactionManager.getTransaction(
                            new DefaultTransactionDefinition()
                    );

            try {
                assertTrue(
                        idempotencyRepository.tryReserve(
                                testUser.getId(),
                                idempotencyKey,
                                "DEPOSIT",
                                "0".repeat(64)
                        )
                );

                blockedCall = executor.submit(
                        () -> transactionIdempotencyService.deposit(
                                idempotencyKey,
                                operation(account, "100.00")
                        )
                );

                assertThrows(
                        TimeoutException.class,
                        () -> blockedCall.get(500, TimeUnit.MILLISECONDS),
                        "The call must wait while the key is reserved."
                );
            } finally {
                transactionManager.rollback(reservingTransaction);
            }

            try {
                TransactionResponseDTO response =
                        blockedCall.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                assertNotNull(response.transactionCode());
            } finally {
                executor.shutdownNow();
            }

            assertBalance("100.00", account);
            assertEquals(1L, countTransactions(account));
            assertEquals(1L, countIdempotencyRequests());
        }

        @Test
        @DisplayName("Should execute a financial operation only once for concurrent identical requests")
        void shouldExecuteOnlyOnceForConcurrentIdenticalRequests()
                throws Exception {

            CheckingAccount account = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            AccountOperationRequestDTO firstRequest =
                    operation(account, "100.00");

            AccountOperationRequestDTO secondRequest =
                    operation(account, "100.00");

            List<Callable<TransactionResponseDTO>> tasks = List.of(
                    () -> transactionIdempotencyService.deposit(
                            idempotencyKey,
                            firstRequest
                    ),
                    () -> transactionIdempotencyService.deposit(
                            idempotencyKey,
                            secondRequest
                    )
            );

            List<TransactionResponseDTO> results = runConcurrently(tasks);

            assertEquals(2, results.size());

            assertSameTransaction(results.get(0), results.get(1));

            assertBalance("100.00", account);
            assertEquals(1L, countTransactions(account));
            assertEquals(1L, countIdempotencyRequests());
        }

        @Test
        @DisplayName("Should allow only one payload when concurrent requests use the same key differently")
        void shouldAllowOnlyOnePayloadForConcurrentConflictingRequests()
                throws Exception {

            CheckingAccount account = createCheckingAccount("0.00");
            UUID idempotencyKey = UUID.randomUUID();

            AccountOperationRequestDTO firstRequest =
                    operation(account, "100.00");

            AccountOperationRequestDTO secondRequest =
                    operation(account, "200.00");

            List<Callable<DepositOutcome>> tasks = List.of(
                    depositOutcome(idempotencyKey, firstRequest),
                    depositOutcome(idempotencyKey, secondRequest)
            );

            List<DepositOutcome> results = runConcurrently(tasks);

            long successfulOperations = results.stream()
                    .filter(result -> !result.conflict())
                    .count();

            long conflicts = results.stream()
                    .filter(DepositOutcome::conflict)
                    .count();

            assertEquals(1L, successfulOperations);
            assertEquals(1L, conflicts);

            DepositOutcome successfulResult = results.stream()
                    .filter(result -> !result.conflict())
                    .findFirst()
                    .orElseThrow();

            assertNotNull(successfulResult.response());

            assertMoneyEquals(
                    successfulResult.response().balanceAfter(),
                    persistedBalance(account)
            );

            assertEquals(1L, countTransactions(account));
            assertEquals(1L, countIdempotencyRequests());
        }
    }

    private Callable<DepositOutcome> depositOutcome(
            UUID idempotencyKey,
            AccountOperationRequestDTO request
    ) {
        return () -> {
            try {
                TransactionResponseDTO response =
                        transactionIdempotencyService.deposit(
                                idempotencyKey,
                                request
                        );

                return new DepositOutcome(response, false);

            } catch (IdempotencyKeyConflictException exception) {
                return new DepositOutcome(null, true);
            }
        };
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks)
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(tasks.size());

        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        ready.countDown();

                        if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Timed out waiting for the start signal."
                            );
                        }

                        return task.call();
                    }))
                    .toList();

            assertTrue(
                    ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Threads did not become ready in time."
            );

            start.countDown();

            List<T> results = new ArrayList<>();

            for (Future<T> future : futures) {
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

    private CheckingAccount createCheckingAccount(String initialBalance) {
        Client client = createClient();

        GeneratedAccountNumber generated =
                accountNumberGenerator.generate();

        CheckingAccount account = new CheckingAccount(
                client,
                generated.number(),
                generated.digit()
        );

        BigDecimal balance = new BigDecimal(initialBalance);
        if (balance.signum() > 0) {
            account.credit(balance);
        } else if (balance.signum() < 0) {
            throw new IllegalArgumentException(
                    "An initial account balance cannot be negative."
            );
        }

        return accountRepository.saveAndFlush(account);
    }

    private Client createClient() {
        int number = ++clientSequence;

        Address address = new Address(
                "Rua Teste",
                String.valueOf(number),
                null,
                "Centro",
                "Mogi das Cruzes",
                State.SP,
                "08700000"
        );

        Client client = new Client(
                "Cliente Idempotencia " + number,
                String.format("%011d", number),
                "idempotency" + number + "@test.com",
                "11999999999",
                address
        );

        return clientRepository.saveAndFlush(client);
    }

    private AccountOperationRequestDTO operation(
            Account account,
            String amount
    ) {
        return new AccountOperationRequestDTO(
                account.getAccountNumber(),
                account.getDigit(),
                new BigDecimal(amount)
        );
    }

    private TransferRequestDTO transfer(
            Account source,
            Account destination,
            String amount
    ) {
        return new TransferRequestDTO(
                source.getAccountNumber(),
                source.getDigit(),
                destination.getAccountNumber(),
                destination.getDigit(),
                new BigDecimal(amount)
        );
    }

    private void assertSameTransaction(
            TransactionResponseDTO expected,
            TransactionResponseDTO actual
    ) {
        assertEquals(expected.transactionCode(), actual.transactionCode());
        assertEquals(expected.transferCode(), actual.transferCode());
        assertEquals(expected.type(), actual.type());
        assertMoneyEquals(expected.amount(), actual.amount());
        assertEquals(expected.createdAt(), actual.createdAt());
        assertEquals(expected.yieldDate(), actual.yieldDate());
        assertMoneyEquals(expected.balanceAfter(), actual.balanceAfter());
    }

    private void assertBalance(String expected, Account account) {
        assertMoneyEquals(
                new BigDecimal(expected),
                persistedBalance(account)
        );
    }

    private BigDecimal persistedBalance(Account account) {
        return accountRepository.findById(account.getId())
                .orElseThrow()
                .getBalance();
    }

    private void assertMoneyEquals(
            BigDecimal expected,
            BigDecimal actual
    ) {
        assertEquals(
                0,
                expected.compareTo(actual),
                "Expected " + expected + " but was " + actual
        );
    }

    private long countTransactions(Account account) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                Long.class,
                account.getId()
        );
    }

    private long countIdempotencyRequests() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_idempotency_requests",
                Long.class
        );
    }

    private void clearIdempotencyRequests() {
        jdbcTemplate.update("DELETE FROM transaction_idempotency_requests");
    }

    private record DepositOutcome(
            TransactionResponseDTO response,
            boolean conflict
    ) {
    }
}