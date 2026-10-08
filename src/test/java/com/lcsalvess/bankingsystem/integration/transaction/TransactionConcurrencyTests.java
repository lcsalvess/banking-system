package com.lcsalvess.bankingsystem.integration.transaction;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.entity.Account;
import com.lcsalvess.bankingsystem.entity.Address;
import com.lcsalvess.bankingsystem.entity.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.Client;
import com.lcsalvess.bankingsystem.entity.enums.State;
import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import com.lcsalvess.bankingsystem.exception.transaction.InsufficientBalanceException;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.repository.AddressRepository;
import com.lcsalvess.bankingsystem.repository.ClientRepository;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.service.account.AccountNumberGenerator;
import com.lcsalvess.bankingsystem.service.account.GeneratedAccountNumber;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class TransactionConcurrencyTests {

    // Keep at or below the Hikari default pool size (10) so tests never starve on connections.
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

    @MockitoBean
    private CurrentUserService currentUserService;

    private int clientSequence;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        clientRepository.deleteAll();
        addressRepository.deleteAll();

        clientSequence = 0;

        when(currentUserService.getUsername()).thenReturn("test-user");
    }

    @Nested
    @DisplayName("Concurrent withdrawals")
    class ConcurrentWithdrawals {

        @Test
        @DisplayName("Should prevent two withdrawals from overdrawing the account")
        void shouldPreventConcurrentWithdrawalsFromOverdrawingAccount() throws Exception {
            CheckingAccount account = createCheckingAccount("1000.00");

            List<Boolean> results = runConcurrently(List.of(
                    withdrawal(account, "700.00"),
                    withdrawal(account, "700.00")
            ));

            assertEquals(1, countSucceeded(results));
            assertBalance("300.00", account);
            assertEquals(1, countTransactions(account, TransactionType.WITHDRAWAL));
        }

        @Test
        @DisplayName("Should allow only as many withdrawals as the balance supports")
        void shouldAllowOnlyAsManyWithdrawalsAsBalanceSupports() throws Exception {
            CheckingAccount account = createCheckingAccount("1000.00");

            List<Callable<Boolean>> tasks = repeat(THREADS, () -> withdrawal(account, "200.00"));

            List<Boolean> results = runConcurrently(tasks);

            assertEquals(5, countSucceeded(results));
            assertBalance("0.00", account);
            assertEquals(5, countTransactions(account, TransactionType.WITHDRAWAL));
        }
    }

    @Nested
    @DisplayName("Concurrent deposits")
    class ConcurrentDeposits {

        @Test
        @DisplayName("Should not lose any deposit")
        void shouldNotLoseAnyDeposit() throws Exception {
            CheckingAccount account = createCheckingAccount("0.00");

            List<Callable<Boolean>> tasks = repeat(THREADS, () -> deposit(account, "50.00"));

            List<Boolean> results = runConcurrently(tasks);

            assertEquals(THREADS, countSucceeded(results));
            assertBalance("500.00", account);
            assertEquals(THREADS, countTransactions(account, TransactionType.DEPOSIT));
        }

        @Test
        @DisplayName("Should keep balance consistent with mixed deposits and withdrawals")
        void shouldKeepBalanceConsistentWithMixedOperations() throws Exception {
            CheckingAccount account = createCheckingAccount("1000.00");

            List<Callable<Boolean>> tasks = new ArrayList<>();
            tasks.addAll(repeat(THREADS / 2, () -> deposit(account, "200.00")));
            tasks.addAll(repeat(THREADS / 2, () -> withdrawal(account, "200.00")));

            List<Boolean> results = runConcurrently(tasks);

            assertEquals(THREADS, countSucceeded(results));
            assertBalance("1000.00", account);
            assertEquals(THREADS / 2, countTransactions(account, TransactionType.DEPOSIT));
            assertEquals(THREADS / 2, countTransactions(account, TransactionType.WITHDRAWAL));
        }
    }

    @Nested
    @DisplayName("Concurrent transfers")
    class ConcurrentTransfers {

        @Test
        @DisplayName("Should complete concurrent opposite transfers without deadlock")
        void shouldCompleteConcurrentOppositeTransfersWithoutDeadlock() throws Exception {
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
        void shouldNotLoseCreditsWhenMultipleTransfersTargetSameAccount() throws Exception {
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
        void shouldAllowOnlyAsManyConcurrentTransfersAsBalanceSupports() throws Exception {
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
    // Tasks
    // ---------------------------------------------------------------------

    private Callable<Boolean> deposit(Account account, String amount) {
        return () -> {
            transactionService.deposit(operation(account, amount));
            return true;
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
     * Runs all tasks at the same instant (barrier + start latch). Any exception other than
     * the ones handled inside the tasks (deadlock, lock timeout, unexpected failure) is
     * rethrown here, so the test fails with the real cause instead of hanging.
     */
    private List<Boolean> runConcurrently(List<Callable<Boolean>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
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

            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Threads did not become ready in time");
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

    private List<Callable<Boolean>> repeat(int times, Supplier<Callable<Boolean>> factory) {
        return IntStream.range(0, times).mapToObj(i -> factory.get()).toList();
    }

    private long countSucceeded(List<Boolean> results) {
        return results.stream().filter(Boolean::booleanValue).count();
    }

    // ---------------------------------------------------------------------
    // Fixtures and assertions
    // ---------------------------------------------------------------------

    private CheckingAccount createCheckingAccount(String initialBalance) {
        Client client = createClient();
        GeneratedAccountNumber generated = accountNumberGenerator.generate();

        CheckingAccount account = new CheckingAccount(client, generated.number(), generated.digit());
        account.credit(new BigDecimal(initialBalance));

        return accountRepository.saveAndFlush(account);
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
        BigDecimal actual = accountRepository.findById(account.getId()).orElseThrow().getBalance();

        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "Expected balance " + expected + " but was " + actual);
    }

    private long countTransactions(Account account, TransactionType type) {
        return transactionRepository.findByAccountIdOrderByCreatedAtDescIdDesc(account.getId()).stream()
                .filter(transaction -> transaction.getType() == type)
                .count();
    }
}