package com.lcsalvess.bankingsystem.unit.service.transaction;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.*;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.entity.enums.TransactionType;
import com.lcsalvess.bankingsystem.event.transaction.TransactionOperationEvent;
import com.lcsalvess.bankingsystem.event.transaction.TransactionTransferEvent;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotSavingsException;
import com.lcsalvess.bankingsystem.exception.account.AccountNotFoundException;
import com.lcsalvess.bankingsystem.exception.account.AccountsAreSameException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.transaction.BalanceLimitExceededException;
import com.lcsalvess.bankingsystem.exception.transaction.InsufficientBalanceException;
import com.lcsalvess.bankingsystem.exception.transaction.InvalidAmountException;
import com.lcsalvess.bankingsystem.exception.transaction.TransactionNotFoundException;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.specification.TransactionSpecifications;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.account.LockedAccounts;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.transaction.DailyYieldProcessingResult;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    private static final String SAVINGS_NUMBER = "00002";
    private static final String SAVINGS_DIGIT = "7";
    private static final Long SAVINGS_ID = 2L;

    private static final BigDecimal OPERATION_AMOUNT = new BigDecimal("10.00");

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    // 23:30 em São Paulo já é o dia seguinte em UTC.
    // Isso ajuda a detectar problemas de fuso horário.
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 1, 15, 23, 30);

    private static final LocalDate TODAY = NOW.toLocalDate();

    private static final Clock CLOCK =
            Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 1, 15, 10, 30, 45);

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
    @DisplayName("findByAccountNumber(String, String, TransactionType, LocalDate, LocalDate, Pageable)")
    class FindByAccountNumber {

        private static final Pageable PAGEABLE = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        @Test
        @DisplayName("Should return the page of account transactions")
        void shouldReturnThePageOfAccountTransactions() {
            CheckingAccount account = account();

            Transaction deposit = new Transaction(
                    TransactionType.DEPOSIT,
                    new BigDecimal("10.00"),
                    CREATED_AT,
                    account,
                    TEST_USER
            );

            Transaction withdrawal = new Transaction(
                    TransactionType.WITHDRAWAL,
                    new BigDecimal("5.00"),
                    CREATED_AT,
                    account,
                    TEST_USER
            );

            Specification<Transaction> specification = specification();

            Page<Transaction> page = new PageImpl<>(
                    List.of(deposit, withdrawal),
                    PAGEABLE,
                    2
            );

            stubAccountLookup(account);

            when(transactionRepository.findAll(specification, PAGEABLE))
                    .thenReturn(page);

            try (MockedStatic<TransactionSpecifications> specifications =
                         mockStatic(TransactionSpecifications.class)) {

                specifications.when(() ->
                                TransactionSpecifications.byAccountAndFilters(
                                        ACCOUNT_ID,
                                        null,
                                        null,
                                        null
                                ))
                        .thenReturn(specification);

                Page<TransactionResponseDTO> result =
                        transactionService.findByAccountNumber(
                                ACCOUNT_NUMBER,
                                ACCOUNT_DIGIT,
                                null,
                                null,
                                null,
                                PAGEABLE
                        );

                assertEquals(
                        List.of(response(deposit), response(withdrawal)),
                        result.getContent()
                );

                assertEquals(2, result.getTotalElements());
                assertEquals(PAGEABLE, result.getPageable());

                specifications.verify(() ->
                        TransactionSpecifications.byAccountAndFilters(
                                ACCOUNT_ID,
                                null,
                                null,
                                null
                        ));
            }

            verifyAccountLookup(account);
            verify(transactionRepository).findAll(specification, PAGEABLE);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should apply type and date filters, using the whole start and end days")
        void shouldApplyTypeAndDateFilters() {
            CheckingAccount account = account();

            Transaction deposit = new Transaction(
                    TransactionType.DEPOSIT,
                    OPERATION_AMOUNT,
                    CREATED_AT,
                    account,
                    TEST_USER
            );

            Page<Transaction> page = new PageImpl<>(
                    List.of(deposit),
                    PAGEABLE,
                    1
            );

            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 15);

            LocalDateTime expectedStart = startDate.atStartOfDay();
            LocalDateTime expectedEndExclusive =
                    endDate.plusDays(1).atStartOfDay();

            stubAccountLookup(account);

            try (MockedStatic<TransactionSpecifications> specifications =
                         mockStatic(TransactionSpecifications.class)) {

                Specification<Transaction> specification = specification();

                specifications.when(() ->
                                TransactionSpecifications.byAccountAndFilters(
                                        ACCOUNT_ID,
                                        TransactionType.DEPOSIT,
                                        expectedStart,
                                        expectedEndExclusive
                                ))
                        .thenReturn(specification);

                when(transactionRepository.findAll(specification, PAGEABLE))
                        .thenReturn(page);

                Page<TransactionResponseDTO> result =
                        transactionService.findByAccountNumber(
                                ACCOUNT_NUMBER,
                                ACCOUNT_DIGIT,
                                TransactionType.DEPOSIT,
                                startDate,
                                endDate,
                                PAGEABLE
                        );

                assertEquals(List.of(response(deposit)), result.getContent());

                specifications.verify(() ->
                        TransactionSpecifications.byAccountAndFilters(
                                ACCOUNT_ID,
                                TransactionType.DEPOSIT,
                                expectedStart,
                                expectedEndExclusive
                        ));

                verify(transactionRepository).findAll(specification, PAGEABLE);
            }

            verifyAccountLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should return empty page when the account has no transactions")
        void shouldReturnEmptyPageWhenAccountHasNoTransactions() {
            CheckingAccount account = account();

            Specification<Transaction> specification = specification();

            stubAccountLookup(account);

            when(transactionRepository.findAll(specification, PAGEABLE))
                    .thenReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

            try (MockedStatic<TransactionSpecifications> specifications =
                         mockStatic(TransactionSpecifications.class)) {

                specifications.when(() ->
                                TransactionSpecifications.byAccountAndFilters(
                                        ACCOUNT_ID,
                                        null,
                                        null,
                                        null
                                ))
                        .thenReturn(specification);

                Page<TransactionResponseDTO> result =
                        transactionService.findByAccountNumber(
                                ACCOUNT_NUMBER,
                                ACCOUNT_DIGIT,
                                null,
                                null,
                                null,
                                PAGEABLE
                        );

                assertTrue(result.isEmpty());
                assertEquals(0, result.getTotalElements());
            }

            verifyAccountLookup(account);
            verify(transactionRepository).findAll(specification, PAGEABLE);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when account does not exist")
        void shouldThrowWhenAccountDoesNotExist() {
            when(accountService.findEntityByAccountNumber(
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT
            )).thenThrow(
                    new AccountNotFoundException(ApiErrorMessages.ACCOUNT_NOT_FOUND)
            );

            assertThrowsWithMessage(
                    AccountNotFoundException.class,
                    ApiErrorMessages.ACCOUNT_NOT_FOUND,
                    () -> transactionService.findByAccountNumber(
                            ACCOUNT_NUMBER,
                            ACCOUNT_DIGIT,
                            null,
                            null,
                            null,
                            PAGEABLE
                    )
            );

            verify(accountService).findEntityByAccountNumber(
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should append the id tie-breaker when the sort has no id")
        void shouldAppendIdTieBreakerWhenSortHasNoId() {
            CheckingAccount account = account();

            Specification<Transaction> specification = specification();

            Pageable requested = PageRequest.of(
                    0,
                    10,
                    Sort.by("amount").descending()
            );

            stubAccountLookup(account);

            when(transactionRepository.findAll(
                    eq(specification),
                    any(Pageable.class)
            )).thenReturn(
                    new PageImpl<>(List.of(), requested, 0)
            );

            try (MockedStatic<TransactionSpecifications> specifications =
                         mockStatic(TransactionSpecifications.class)) {

                specifications.when(() ->
                                TransactionSpecifications.byAccountAndFilters(
                                        ACCOUNT_ID,
                                        null,
                                        null,
                                        null
                                ))
                        .thenReturn(specification);

                transactionService.findByAccountNumber(
                        ACCOUNT_NUMBER,
                        ACCOUNT_DIGIT,
                        null,
                        null,
                        null,
                        requested
                );
            }

            ArgumentCaptor<Pageable> captor =
                    ArgumentCaptor.forClass(Pageable.class);

            verifyAccountLookup(account);

            verify(transactionRepository).findAll(
                    eq(specification),
                    captor.capture()
            );

            verifyNoMoreInteractionsOnMocks();

            assertEquals(
                    Sort.by(
                            Sort.Order.desc("amount"),
                            Sort.Order.asc("id")
                    ),
                    captor.getValue().getSort()
            );
        }

        private static Specification<Transaction> specification() {
            return (root, query, cb) -> null;
        }
    }

    @Nested
    @DisplayName("findByTransactionCode(UUID)")
    class FindByTransactionCode {

        @Test
        @DisplayName("Should return the transaction when code exists")
        void shouldReturnTheTransactionWhenCodeExists() {
            Transaction transaction = new Transaction(
                    TransactionType.DEPOSIT,
                    OPERATION_AMOUNT,
                    CREATED_AT,
                    account(),
                    TEST_USER
            );

            when(transactionRepository.findByTransactionCode(
                    transaction.getTransactionCode()
            )).thenReturn(Optional.of(transaction));

            TransactionResponseDTO result =
                    transactionService.findByTransactionCode(
                            transaction.getTransactionCode()
                    );

            assertEquals(response(transaction), result);

            verify(transactionRepository).findByTransactionCode(
                    transaction.getTransactionCode()
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when transaction does not exist")
        void shouldThrowWhenTransactionDoesNotExist() {
            UUID transactionCode = UUID.randomUUID();

            when(transactionRepository.findByTransactionCode(transactionCode))
                    .thenReturn(Optional.empty());

            assertThrowsWithMessage(
                    TransactionNotFoundException.class,
                    ApiErrorMessages.TRANSACTION_NOT_FOUND,
                    () -> transactionService.findByTransactionCode(transactionCode)
            );

            verify(transactionRepository).findByTransactionCode(transactionCode);
            verifyNoMoreInteractionsOnMocks();
        }
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
                            USERNAME
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
            account.cancel();

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
                            USERNAME
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
            account.cancel();

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
                            USERNAME
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

            source.cancel();

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

            destination.cancel();

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

    @Nested
    @DisplayName("processDailyYieldForDate(...)")
    class ProcessDailyYieldForDate {

        @Test
        @DisplayName("Should apply yield and advance processing date")
        void shouldApplyYieldAndAdvanceProcessingDate() {
            SavingsAccount account = eligibleSavingsAccount("1000.00");

            stubAccountOperationLookup(account);

            when(transactionRepository.existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            )).thenReturn(false);

            when(transactionRepository.calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    TODAY.atStartOfDay()
            )).thenReturn(new BigDecimal("1000.00"));

            stubSaveReturningArgument();

            DailyYieldProcessingResult result =
                    transactionService.processDailyYieldForDate(
                            SAVINGS_NUMBER,
                            SAVINGS_DIGIT,
                            TODAY
                    );

            assertEquals(DailyYieldProcessingResult.APPLIED, result);
            assertBalance("1000.17", account);
            assertEquals(TODAY, account.getLastYieldDate());
            assertEquals(TODAY, account.getLastYieldProcessedDate());

            verifyAccountOperationLookup(account);
            verify(transactionRepository).existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            );
            verify(transactionRepository).calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    TODAY.atStartOfDay()
            );
            verify(transactionRepository).save(any(Transaction.class));
            verify(eventPublisher).publishEvent(
                    new TransactionOperationEvent(
                            TransactionType.YIELD,
                            SAVINGS_NUMBER,
                            new BigDecimal("0.17"),
                            "SYSTEM"
                    )
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should apply yield when the calculated amount is one cent")
        void shouldApplyYieldWhenCalculatedAmountIsOneCent() {
            SavingsAccount account = eligibleSavingsAccount("40.00");

            stubAccountOperationLookup(account);

            when(transactionRepository.existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            )).thenReturn(false);

            when(transactionRepository.calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    TODAY.atStartOfDay()
            )).thenReturn(new BigDecimal("40.00"));

            stubSaveReturningArgument();

            DailyYieldProcessingResult result =
                    transactionService.processDailyYieldForDate(
                            SAVINGS_NUMBER,
                            SAVINGS_DIGIT,
                            TODAY
                    );

            assertEquals(DailyYieldProcessingResult.APPLIED, result);
            assertBalance("40.01", account);
            assertEquals(TODAY, account.getLastYieldDate());
            assertEquals(TODAY, account.getLastYieldProcessedDate());

            verifyAccountOperationLookup(account);
            verify(transactionRepository).existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            );
            verify(transactionRepository).calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    TODAY.atStartOfDay()
            );
            verify(transactionRepository).save(any(Transaction.class));
            verify(eventPublisher).publishEvent(
                    new TransactionOperationEvent(
                            TransactionType.YIELD,
                            SAVINGS_NUMBER,
                            new BigDecimal("0.01"),
                            "SYSTEM"
                    )
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should reject an account that is not a savings account")
        void shouldRejectAccountThatIsNotSavings() {
            CheckingAccount account = account();

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    AccountIsNotSavingsException.class,
                    ApiErrorMessages.ACCOUNT_NOT_SAVINGS,
                    () -> transactionService.processDailyYieldForDate(
                            ACCOUNT_NUMBER,
                            ACCOUNT_DIGIT,
                            TODAY
                    )
            );

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should reject an inactive savings account")
        void shouldRejectInactiveSavingsAccount() {
            SavingsAccount account = eligibleSavingsAccount("1000.00");
            account.cancel();

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ApiErrorMessages.ACCOUNT_NOT_ACTIVE,
                    () -> transactionService.processDailyYieldForDate(
                            SAVINGS_NUMBER,
                            SAVINGS_DIGIT,
                            TODAY
                    )
            );

            assertBalance("1000.00", account);
            assertEquals(TODAY.minusDays(1), account.getLastYieldDate());

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should return already processed when yield already exists")
        void shouldReturnAlreadyProcessedWhenYieldAlreadyExists() {
            SavingsAccount account = eligibleSavingsAccount("1000.00");

            stubAccountOperationLookup(account);

            when(transactionRepository.existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            )).thenReturn(true);

            DailyYieldProcessingResult result =
                    transactionService.processDailyYieldForDate(
                            SAVINGS_NUMBER,
                            SAVINGS_DIGIT,
                            TODAY
                    );

            assertEquals(
                    DailyYieldProcessingResult.ALREADY_PROCESSED,
                    result
            );
            assertBalance("1000.00", account);
            assertEquals(TODAY, account.getLastYieldDate());
            assertEquals(TODAY, account.getLastYieldProcessedDate());

            verifyAccountOperationLookup(account);
            verify(transactionRepository).existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should reject a future reference date")
        void shouldRejectFutureReferenceDate() {
            assertThrowsWithMessage(
                    IllegalArgumentException.class,
                    "A data de referência não pode estar no futuro.",
                    () -> transactionService.processDailyYieldForDate(
                            SAVINGS_NUMBER,
                            SAVINGS_DIGIT,
                            TODAY.plusDays(1)
                    )
            );

            verifyNoInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should advance processing date when yield rounds to zero")
        void shouldAdvanceProcessingDateWhenYieldRoundsToZero() {
            SavingsAccount account = eligibleSavingsAccount("0.99");

            stubAccountOperationLookup(account);

            when(transactionRepository.existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            )).thenReturn(false);

            when(transactionRepository.calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    TODAY.atStartOfDay()
            )).thenReturn(new BigDecimal("0.99"));

            DailyYieldProcessingResult result =
                    transactionService.processDailyYieldForDate(
                            SAVINGS_NUMBER,
                            SAVINGS_DIGIT,
                            TODAY
                    );

            assertEquals(
                    DailyYieldProcessingResult.NO_YIELD_AVAILABLE,
                    result
            );
            assertBalance("0.99", account);
            assertEquals(TODAY.minusDays(1), account.getLastYieldDate());
            assertEquals(TODAY, account.getLastYieldProcessedDate());

            verifyAccountOperationLookup(account);
            verify(transactionRepository).existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            );
            verify(transactionRepository).calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    TODAY.atStartOfDay()
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should mark date processed when yield exceeds balance limit")
        void shouldMarkDateProcessedWhenYieldExceedsBalanceLimit() {
            SavingsAccount account = eligibleSavingsAccount(
                    Account.MAX_BALANCE.toPlainString()
            );

            stubAccountOperationLookup(account);

            when(transactionRepository.existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            )).thenReturn(false);

            when(transactionRepository.calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    TODAY.atStartOfDay()
            )).thenReturn(Account.MAX_BALANCE);

            DailyYieldProcessingResult result =
                    transactionService.processDailyYieldForDate(
                            SAVINGS_NUMBER,
                            SAVINGS_DIGIT,
                            TODAY
                    );

            assertEquals(
                    DailyYieldProcessingResult.BALANCE_LIMIT_REACHED,
                    result
            );
            assertBalance(Account.MAX_BALANCE.toPlainString(), account);
            assertEquals(TODAY.minusDays(1), account.getLastYieldDate());
            assertEquals(TODAY, account.getLastYieldProcessedDate());

            verifyAccountOperationLookup(account);
            verify(transactionRepository).existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            );
            verify(transactionRepository).calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    TODAY.atStartOfDay()
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

    private void stubAccountLookup(Account account) {
        when(accountService.findEntityByAccountNumber(
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

    private void verifyAccountLookup(Account account) {
        verify(accountService).findEntityByAccountNumber(
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
        assertEquals(NOW, transaction.getCreatedAt());
    }

    private static TransactionResponseDTO response(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.getTransactionCode(),
                transaction.getTransferCode(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCreatedAt()
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

    private static SavingsAccount savingsAccount(
            String balance,
            LocalDate lastYieldDate
    ) {
        SavingsAccount account = new SavingsAccount(
                null,
                SAVINGS_NUMBER,
                SAVINGS_DIGIT,
                lastYieldDate
        );

        ReflectionTestUtils.setField(account, "id", SAVINGS_ID);

        account.credit(new BigDecimal(balance));

        return account;
    }

    private static SavingsAccount eligibleSavingsAccount(String balance) {
        // A conta recebeu rendimento ontem, portanto está elegível hoje.
        return savingsAccount(
                balance,
                TODAY.minusDays(1)
        );
    }
}