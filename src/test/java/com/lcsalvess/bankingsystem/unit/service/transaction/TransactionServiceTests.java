package com.lcsalvess.bankingsystem.unit.service.transaction;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.event.transaction.TransactionOperationEvent;
import com.lcsalvess.bankingsystem.event.transaction.TransactionTransferEvent;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.account.AccountNotFoundException;
import com.lcsalvess.bankingsystem.exception.account.AccountsAreSameException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.transaction.BalanceLimitExceededException;
import com.lcsalvess.bankingsystem.exception.transaction.InsufficientBalanceException;
import com.lcsalvess.bankingsystem.exception.transaction.InvalidAmountException;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.account.LockedAccounts;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.service.transaction.TransactionServiceTests#";

    private static final String USERNAME = "test-user";

    private static final User TEST_USER = new User(
            USERNAME,
            "test-user@test.com",
            "test-password",
            Role.EMPLOYEE
    );

    private static final String ACCOUNT_NUMBER = "00001";
    private static final String ACCOUNT_DIGIT = "9";
    private static final Long ACCOUNT_ID = 1L;

    private static final String DESTINATION_NUMBER = "00003";
    private static final String DESTINATION_DIGIT = "5";
    private static final Long DESTINATION_ID = 3L;

    private static final BigDecimal OPERATION_AMOUNT = new BigDecimal("10.00");

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    // 23:30 em São Paulo já é o dia seguinte em UTC.
    // Isso ajuda a detectar problemas de fuso horário.
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 1, 15, 23, 30);

    private static final Clock CLOCK =
            Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository,
                accountService,
                currentUserService,
                eventPublisher,
                CLOCK
        );
    }

    @Nested
    @DisplayName("deposit(AccountOperationRequestDTO)")
    class Deposit {

        @Test
        @DisplayName("Should deposit successfully")
        void shouldDepositSuccessfully() {
            CheckingAccount account = account();

            AccountOperationRequestDTO request = operationRequest(OPERATION_AMOUNT);

            stubAccountOperationLookup(account);
            when(currentUserService.getCurrentUser()).thenReturn(TEST_USER);
            stubSaveReturningArgument();

            TransactionResponseDTO result = transactionService.deposit(request);

            assertBalance("110.00", account);

            ArgumentCaptor<Transaction> transactionCaptor =
                    ArgumentCaptor.forClass(Transaction.class);

            verifyAccountOperationLookup(account);
            verify(currentUserService).getCurrentUser();
            verify(transactionRepository).save(transactionCaptor.capture());

            verify(eventPublisher).publishEvent(
                    new TransactionOperationEvent(
                            TransactionType.DEPOSIT,
                            ACCOUNT_NUMBER,
                            OPERATION_AMOUNT,
                            USERNAME,
                            transactionCaptor.getValue().getTransactionCode()
                    )
            );

            verifyNoMoreInteractionsOnMocks();

            assertTransaction(
                    transactionCaptor.getValue(),
                    TransactionType.DEPOSIT,
                    OPERATION_AMOUNT,
                    account
            );

            assertEquals(response(transactionCaptor.getValue()), result);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAmounts")
        @DisplayName("Should throw when amount is invalid")
        void shouldThrowWhenAmountIsInvalid(
                String scenario,
                BigDecimal amount
        ) {
            assertThrowsWithMessage(
                    InvalidAmountException.class,
                    ApiErrorMessages.INVALID_AMOUNT,
                    () -> transactionService.deposit(operationRequest(amount))
            );

            verifyNoInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account is not active")
        void shouldThrowWhenAccountIsNotActive() {
            CheckingAccount account = account();
            ReflectionTestUtils.setField(account, "status", AccountStatus.CANCELLED);

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ApiErrorMessages.ACCOUNT_NOT_ACTIVE,
                    () -> transactionService.deposit(operationRequest(OPERATION_AMOUNT))
            );

            assertBalance("100.00", account);

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when the deposit would exceed the maximum balance")
        void shouldThrowWhenDepositExceedsMaximumBalance() {
            CheckingAccount account = account();

            BigDecimal amount = Account.MAX_BALANCE
                    .subtract(new BigDecimal("100.00"))
                    .add(new BigDecimal("0.01"));

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    BalanceLimitExceededException.class,
                    ApiErrorMessages.BALANCE_LIMIT_EXCEEDED,
                    () -> transactionService.deposit(operationRequest(amount))
            );

            assertBalance("100.00", account);

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should accept a deposit that brings the balance exactly to the maximum")
        void shouldAcceptDepositThatReachesMaximumBalanceExactly() {
            CheckingAccount account = account();

            BigDecimal amount = Account.MAX_BALANCE
                    .subtract(new BigDecimal("100.00"));

            stubAccountOperationLookup(account);
            when(currentUserService.getCurrentUser()).thenReturn(TEST_USER);
            stubSaveReturningArgument();

            transactionService.deposit(operationRequest(amount));

            assertEquals(Account.MAX_BALANCE, account.getBalance());
        }
    }

    @Nested
    @DisplayName("withdraw(AccountOperationRequestDTO)")
    class Withdraw {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "withdrawals")
        @DisplayName("Should withdraw successfully")
        void shouldWithdrawSuccessfully(
                String scenario,
                BigDecimal amount,
                String expectedBalance
        ) {
            CheckingAccount account = account();

            stubAccountOperationLookup(account);
            when(currentUserService.getCurrentUser()).thenReturn(TEST_USER);
            stubSaveReturningArgument();

            TransactionResponseDTO result =
                    transactionService.withdraw(operationRequest(amount));

            assertBalance(expectedBalance, account);

            ArgumentCaptor<Transaction> transactionCaptor =
                    ArgumentCaptor.forClass(Transaction.class);

            verifyAccountOperationLookup(account);
            verify(currentUserService).getCurrentUser();
            verify(transactionRepository).save(transactionCaptor.capture());

            verify(eventPublisher).publishEvent(
                    new TransactionOperationEvent(
                            TransactionType.WITHDRAWAL,
                            ACCOUNT_NUMBER,
                            amount,
                            USERNAME,
                            transactionCaptor.getValue().getTransactionCode()
                    )
            );

            verifyNoMoreInteractionsOnMocks();

            assertTransaction(
                    transactionCaptor.getValue(),
                    TransactionType.WITHDRAWAL,
                    amount,
                    account
            );

            assertEquals(response(transactionCaptor.getValue()), result);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAmounts")
        @DisplayName("Should throw when amount is invalid")
        void shouldThrowWhenAmountIsInvalid(
                String scenario,
                BigDecimal amount
        ) {
            assertThrowsWithMessage(
                    InvalidAmountException.class,
                    ApiErrorMessages.INVALID_AMOUNT,
                    () -> transactionService.withdraw(operationRequest(amount))
            );

            verifyNoInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "amountsAboveBalance")
        @DisplayName("Should throw when amount exceeds balance")
        void shouldThrowWhenAmountExceedsBalance(
                String scenario,
                BigDecimal amount
        ) {
            CheckingAccount account = account();

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    InsufficientBalanceException.class,
                    ApiErrorMessages.INSUFFICIENT_BALANCE,
                    () -> transactionService.withdraw(operationRequest(amount))
            );

            assertBalance("100.00", account);

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account is not active")
        void shouldThrowWhenAccountIsNotActive() {
            CheckingAccount account = account();
            ReflectionTestUtils.setField(account, "status", AccountStatus.CANCELLED);

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ApiErrorMessages.ACCOUNT_NOT_ACTIVE,
                    () -> transactionService.withdraw(operationRequest(OPERATION_AMOUNT))
            );

            assertBalance("100.00", account);

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("transfer(TransferRequestDTO)")
    class Transfer {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "transfers")
        @DisplayName("Should transfer successfully")
        void shouldTransferSuccessfully(
                String scenario,
                BigDecimal amount,
                String expectedSourceBalance,
                String expectedDestinationBalance
        ) {
            CheckingAccount source = account();
            CheckingAccount destination = destinationAccount();

            stubTransferAccountLookup(source, destination);
            when(currentUserService.getCurrentUser()).thenReturn(TEST_USER);
            stubSaveReturningArgument();

            TransactionResponseDTO result =
                    transactionService.transfer(transferRequest(amount));

            assertBalance(expectedSourceBalance, source);
            assertBalance(expectedDestinationBalance, destination);

            ArgumentCaptor<Transaction> transactionCaptor =
                    ArgumentCaptor.forClass(Transaction.class);

            verifyTransferAccountLookup(source, destination);
            verify(currentUserService).getCurrentUser();
            verify(transactionRepository, times(2))
                    .save(transactionCaptor.capture());

            verify(eventPublisher).publishEvent(
                    new TransactionTransferEvent(
                            TransactionType.TRANSFER_SENT,
                            ACCOUNT_NUMBER,
                            DESTINATION_NUMBER,
                            amount,
                            USERNAME,
                            transactionCaptor.getAllValues().getFirst().getTransferCode()
                    )
            );

            verifyNoMoreInteractionsOnMocks();

            List<Transaction> saved = transactionCaptor.getAllValues();

            Transaction sent = saved.get(0);
            Transaction received = saved.get(1);

            assertTransaction(
                    sent,
                    TransactionType.TRANSFER_SENT,
                    amount,
                    source
            );

            assertTransaction(
                    received,
                    TransactionType.TRANSFER_RECEIVED,
                    amount,
                    destination
            );

            assertNotNull(sent.getTransferCode());
            assertEquals(sent.getTransferCode(), received.getTransferCode());
            assertNotEquals(sent.getTransactionCode(), received.getTransactionCode());

            assertEquals(response(sent), result);
        }

        @Test
        @DisplayName("Should throw when the transfer would exceed the destination maximum balance")
        void shouldThrowWhenTransferExceedsDestinationMaximumBalance() {
            CheckingAccount source = account();

            CheckingAccount destination = checkingAccount(
                    DESTINATION_NUMBER,
                    DESTINATION_DIGIT,
                    DESTINATION_ID,
                    Account.MAX_BALANCE
                            .subtract(new BigDecimal("10.00"))
                            .toPlainString()
            );

            stubTransferAccountLookup(source, destination);

            assertThrowsWithMessage(
                    BalanceLimitExceededException.class,
                    ApiErrorMessages.BALANCE_LIMIT_EXCEEDED,
                    () -> transactionService.transfer(
                            transferRequest(new BigDecimal("10.01"))
                    )
            );

            assertBalance("100.00", source);

            assertEquals(
                    Account.MAX_BALANCE.subtract(new BigDecimal("10.00")),
                    destination.getBalance()
            );

            verifyTransferAccountLookup(source, destination);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when accounts are the same")
        void shouldThrowWhenAccountsAreTheSame() {
            TransferRequestDTO request = new TransferRequestDTO(
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT,
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT,
                    OPERATION_AMOUNT
            );

            assertThrowsWithMessage(
                    AccountsAreSameException.class,
                    ApiErrorMessages.SAME_SOURCE_AND_DESTINATION_ACCOUNT,
                    () -> transactionService.transfer(request)
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when source account is not active")
        void shouldThrowWhenSourceAccountIsNotActive() {
            CheckingAccount source = account();
            CheckingAccount destination = destinationAccount();

            ReflectionTestUtils.setField(source, "status", AccountStatus.CANCELLED);

            stubTransferAccountLookup(source, destination);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ApiErrorMessages.ACCOUNT_NOT_ACTIVE,
                    () -> transactionService.transfer(transferRequest(OPERATION_AMOUNT))
            );

            assertBalance("100.00", source);
            assertBalance("50.00", destination);

            verifyTransferAccountLookup(source, destination);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when destination account is not active")
        void shouldThrowWhenDestinationAccountIsNotActive() {
            CheckingAccount source = account();
            CheckingAccount destination = destinationAccount();

            ReflectionTestUtils.setField(destination, "status", AccountStatus.CANCELLED);

            stubTransferAccountLookup(source, destination);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ApiErrorMessages.ACCOUNT_NOT_ACTIVE,
                    () -> transactionService.transfer(transferRequest(OPERATION_AMOUNT))
            );

            assertBalance("100.00", source);
            assertBalance("50.00", destination);

            verifyTransferAccountLookup(source, destination);
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidAmounts")
        @DisplayName("Should throw when amount is invalid")
        void shouldThrowWhenAmountIsInvalid(
                String scenario,
                BigDecimal amount
        ) {
            assertThrowsWithMessage(
                    InvalidAmountException.class,
                    ApiErrorMessages.INVALID_AMOUNT,
                    () -> transactionService.transfer(transferRequest(amount))
            );

            verifyNoInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "amountsAboveBalance")
        @DisplayName("Should throw when amount exceeds source balance")
        void shouldThrowWhenAmountExceedsSourceBalance(
                String scenario,
                BigDecimal amount
        ) {
            CheckingAccount source = account();
            CheckingAccount destination = destinationAccount();

            stubTransferAccountLookup(source, destination);

            assertThrowsWithMessage(
                    InsufficientBalanceException.class,
                    ApiErrorMessages.INSUFFICIENT_BALANCE,
                    () -> transactionService.transfer(transferRequest(amount))
            );

            assertBalance("100.00", source);
            assertBalance("50.00", destination);

            verifyTransferAccountLookup(source, destination);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should not change balances when destination account does not exist")
        void shouldNotChangeBalancesWhenDestinationAccountDoesNotExist() {
            CheckingAccount source = account();

            when(accountService.lockAccountsForTransfer(
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT,
                    DESTINATION_NUMBER,
                    DESTINATION_DIGIT
            )).thenThrow(
                    new AccountNotFoundException(ApiErrorMessages.ACCOUNT_NOT_FOUND)
            );

            assertThrowsWithMessage(
                    AccountNotFoundException.class,
                    ApiErrorMessages.ACCOUNT_NOT_FOUND,
                    () -> transactionService.transfer(transferRequest(OPERATION_AMOUNT))
            );

            assertBalance("100.00", source);

            verify(accountService).lockAccountsForTransfer(
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT,
                    DESTINATION_NUMBER,
                    DESTINATION_DIGIT
            );

            verifyNoMoreInteractionsOnMocks();
        }
    }


    @SuppressWarnings("unused")
    static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of("amount is null", null),
                Arguments.of("amount is zero", BigDecimal.ZERO),
                Arguments.of("amount is negative", new BigDecimal("-1.00"))
        );
    }

    // Saldo inicial da conta de origem: 100.00.
    @SuppressWarnings("unused")
    static Stream<Arguments> amountsAboveBalance() {
        return Stream.of(
                Arguments.of(
                        "amount exceeds balance",
                        new BigDecimal("200.00")
                ),
                Arguments.of(
                        "amount is one cent above balance",
                        new BigDecimal("100.01")
                )
        );
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> withdrawals() {
        return Stream.of(
                Arguments.of(
                        "amount is less than balance",
                        new BigDecimal("30.00"),
                        "70.00"
                ),
                Arguments.of(
                        "amount equals balance",
                        new BigDecimal("100.00"),
                        "0.00"
                )
        );
    }

    // Saldos iniciais: origem 100.00, destino 50.00.
    @SuppressWarnings("unused")
    static Stream<Arguments> transfers() {
        return Stream.of(
                Arguments.of(
                        "amount is less than source balance",
                        new BigDecimal("30.00"),
                        "70.00",
                        "80.00"
                ),
                Arguments.of(
                        "amount equals source balance",
                        new BigDecimal("100.00"),
                        "0.00",
                        "150.00"
                )
        );
    }

    private void stubAccountOperationLookup(Account account) {
        when(accountService.findEntityByAccountNumberForUpdate(
                account.getAccountNumber(),
                account.getDigit()
        )).thenReturn(account);
    }

    private void stubTransferAccountLookup(
            Account source,
            Account destination
    ) {
        when(accountService.lockAccountsForTransfer(
                source.getAccountNumber(),
                source.getDigit(),
                destination.getAccountNumber(),
                destination.getDigit()
        )).thenReturn(
                new LockedAccounts(source, destination)
        );
    }

    private void verifyAccountOperationLookup(Account account) {
        verify(accountService).findEntityByAccountNumberForUpdate(
                account.getAccountNumber(),
                account.getDigit()
        );
    }

    private void verifyTransferAccountLookup(
            Account source,
            Account destination
    ) {
        verify(accountService).lockAccountsForTransfer(
                source.getAccountNumber(),
                source.getDigit(),
                destination.getAccountNumber(),
                destination.getDigit()
        );
    }

    private void stubSaveReturningArgument() {
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void verifyNoInteractionsOnMocks() {
        verifyNoInteractions(
                transactionRepository,
                accountService,
                currentUserService,
                eventPublisher
        );
    }

    private void verifyNoMoreInteractionsOnMocks() {
        verifyNoMoreInteractions(
                transactionRepository,
                accountService,
                currentUserService,
                eventPublisher
        );
    }

    private static void assertThrowsWithMessage(
            Class<? extends Exception> type,
            String message,
            Executable executable
    ) {
        Exception exception = assertThrows(type, executable);
        assertEquals(message, exception.getMessage());
    }

    private static void assertBalance(
            String expected,
            Account account
    ) {
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(account.getBalance())
        );
    }

    // Este helper continua sendo exclusivo de transações realizadas por usuários.
    private static void assertTransaction(
            Transaction transaction,
            TransactionType type,
            BigDecimal amount,
            Account account
    ) {
        assertEquals(type, transaction.getType());

        assertEquals(
                0,
                amount.compareTo(transaction.getAmount())
        );

        assertSame(account, transaction.getAccount());
        assertSame(TEST_USER, transaction.getPerformedBy());
        assertFalse(transaction.isPerformedBySystem());
        assertEquals(CLOCK.instant(), transaction.getCreatedAt());
    }

    private static TransactionResponseDTO response(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.getTransactionCode(),
                transaction.getTransferCode(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCreatedAt(),
                transaction.getYieldDate(),
                transaction.getBalanceAfter()
        );
    }

    private static AccountOperationRequestDTO operationRequest(
            BigDecimal amount
    ) {
        return new AccountOperationRequestDTO(
                ACCOUNT_NUMBER,
                ACCOUNT_DIGIT,
                amount
        );
    }

    private static TransferRequestDTO transferRequest(
            BigDecimal amount
    ) {
        return new TransferRequestDTO(
                ACCOUNT_NUMBER,
                ACCOUNT_DIGIT,
                DESTINATION_NUMBER,
                DESTINATION_DIGIT,
                amount
        );
    }

    private static CheckingAccount account() {
        return checkingAccount(
                ACCOUNT_NUMBER,
                ACCOUNT_DIGIT,
                ACCOUNT_ID,
                "100.00"
        );
    }

    private static CheckingAccount destinationAccount() {
        return checkingAccount(
                DESTINATION_NUMBER,
                DESTINATION_DIGIT,
                DESTINATION_ID,
                "50.00"
        );
    }

    private static CheckingAccount checkingAccount(
            String number,
            String digit,
            Long id,
            String balance
    ) {
        // O serviço não acessa o cliente da conta nesses testes.
        CheckingAccount account = new CheckingAccount(
                null,
                number,
                digit
        );

        ReflectionTestUtils.setField(account, "id", id);

        account.credit(new BigDecimal(balance));

        return account;
    }
}
