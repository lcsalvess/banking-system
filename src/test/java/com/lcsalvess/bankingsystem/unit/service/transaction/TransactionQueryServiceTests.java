package com.lcsalvess.bankingsystem.unit.service.transaction;

import com.lcsalvess.bankingsystem.dto.response.transaction.TransactionResponseDTO;
import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.account.AccountNotFoundException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.transaction.InvalidTransactionSnapshotException;
import com.lcsalvess.bankingsystem.exception.transaction.TransactionNotFoundException;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.specification.TransactionSpecifications;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionPageResult;
import com.lcsalvess.bankingsystem.service.transaction.query.TransactionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionQueryServiceTests {

    private static final User TEST_USER = new User(
            "test-user",
            "test-user@test.com",
            "test-password",
            Role.EMPLOYEE
    );

    private static final String ACCOUNT_NUMBER = "00001";
    private static final String ACCOUNT_DIGIT = "9";
    private static final Long ACCOUNT_ID = 1L;

    private static final BigDecimal OPERATION_AMOUNT =
            new BigDecimal("10.00");

    private static final ZoneId ZONE =
            ZoneId.of("America/Sao_Paulo");

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 1, 15, 23, 30);

    private static final Clock CLOCK =
            Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private static final Instant CREATED_AT =
            Instant.parse("2026-01-15T13:30:45Z");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    private TransactionQueryService transactionQueryService;

    @BeforeEach
    void setUp() {
        transactionQueryService = new TransactionQueryService(
                transactionRepository,
                accountService,
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

            Transaction deposit = Transaction.create(
                    TransactionType.DEPOSIT,
                    new BigDecimal("10.00"),
                    CREATED_AT,
                    account,
                    TEST_USER,
                    new BigDecimal("10.00")
            );

            Transaction withdrawal = Transaction.create(
                    TransactionType.WITHDRAWAL,
                    new BigDecimal("5.00"),
                    CREATED_AT,
                    account,
                    TEST_USER,
                    new BigDecimal("5.00")
            );

            stubLatestTransaction(account, withdrawal, 2L);

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

                stubSpecification(
                        specifications,
                        specification,
                        null,
                        null,
                        null,
                        2L
                );

                Page<TransactionResponseDTO> result =
                        transactionQueryService.findByAccountNumber(
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

                verifySpecification(
                        specifications,
                        null,
                        null,
                        null,
                        2L,
                        1
                );
            }

            verifyAccountLookup(account);
            verify(transactionRepository).findFirstByAccount_IdOrderByIdDesc(
                    ACCOUNT_ID
            );
            verify(transactionRepository).findAll(specification, PAGEABLE);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should apply type and date filters, using the whole start and end days")
        void shouldApplyTypeAndDateFilters() {
            CheckingAccount account = account();

            Transaction deposit = Transaction.create(
                    TransactionType.DEPOSIT,
                    OPERATION_AMOUNT,
                    CREATED_AT,
                    account,
                    TEST_USER,
                    OPERATION_AMOUNT
            );

            stubLatestTransaction(account, deposit, 1L);

            Page<Transaction> page = new PageImpl<>(
                    List.of(deposit),
                    PAGEABLE,
                    1
            );

            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 15);

            Instant expectedStart =
                    startDate.atStartOfDay(ZONE).toInstant();

            Instant expectedEndExclusive =
                    endDate.plusDays(1)
                            .atStartOfDay(ZONE)
                            .toInstant();

            stubAccountLookup(account);

            try (MockedStatic<TransactionSpecifications> specifications =
                         mockStatic(TransactionSpecifications.class)) {

                Specification<Transaction> specification = specification();

                stubSpecification(
                        specifications,
                        specification,
                        TransactionType.DEPOSIT,
                        expectedStart,
                        expectedEndExclusive,
                        1L
                );

                when(transactionRepository.findAll(specification, PAGEABLE))
                        .thenReturn(page);

                Page<TransactionResponseDTO> result =
                        transactionQueryService.findByAccountNumber(
                                ACCOUNT_NUMBER,
                                ACCOUNT_DIGIT,
                                TransactionType.DEPOSIT,
                                startDate,
                                endDate,
                                PAGEABLE
                        );

                assertEquals(
                        List.of(response(deposit)),
                        result.getContent()
                );

                verifySpecification(
                        specifications,
                        TransactionType.DEPOSIT,
                        expectedStart,
                        expectedEndExclusive,
                        1L,
                        1
                );

                verify(transactionRepository).findAll(
                        specification,
                        PAGEABLE
                );
            }

            verifyAccountLookup(account);
            verify(transactionRepository).findFirstByAccount_IdOrderByIdDesc(
                    ACCOUNT_ID
            );
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should return empty page when the account has no transactions")
        void shouldReturnEmptyPageWhenAccountHasNoTransactions() {
            CheckingAccount account = account();

            Specification<Transaction> specification = specification();

            stubAccountLookup(account);

            when(transactionRepository.findFirstByAccount_IdOrderByIdDesc(
                    ACCOUNT_ID
            )).thenReturn(Optional.empty());

            when(transactionRepository.findAll(specification, PAGEABLE))
                    .thenReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

            try (MockedStatic<TransactionSpecifications> specifications =
                         mockStatic(TransactionSpecifications.class)) {

                stubSpecification(
                        specifications,
                        specification,
                        null,
                        null,
                        null,
                        0L
                );

                Page<TransactionResponseDTO> result =
                        transactionQueryService.findByAccountNumber(
                                ACCOUNT_NUMBER,
                                ACCOUNT_DIGIT,
                                null,
                                null,
                                null,
                                PAGEABLE
                        );

                assertTrue(result.isEmpty());
                assertEquals(0, result.getTotalElements());

                verifySpecification(
                        specifications,
                        null,
                        null,
                        null,
                        0L,
                        1
                );
            }

            verifyAccountLookup(account);
            verify(transactionRepository).findFirstByAccount_IdOrderByIdDesc(
                    ACCOUNT_ID
            );
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
                    new AccountNotFoundException(
                            ApiErrorMessages.ACCOUNT_NOT_FOUND
                    )
            );

            assertThrowsWithMessage(
                    AccountNotFoundException.class,
                    ApiErrorMessages.ACCOUNT_NOT_FOUND,
                    () -> transactionQueryService.findByAccountNumber(
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

            Transaction latestTransaction = transactionWithId(
                    TransactionType.DEPOSIT,
                    account,
                    1L
            );

            stubLatestTransaction(account, latestTransaction, 1L);

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

                stubSpecification(
                        specifications,
                        specification,
                        null,
                        null,
                        null,
                        1L
                );

                transactionQueryService.findByAccountNumber(
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

            verify(transactionRepository).findFirstByAccount_IdOrderByIdDesc(
                    ACCOUNT_ID
            );

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

        @Test
        @DisplayName("Should reuse the original snapshot when a new transaction is created between page requests")
        void shouldReuseOriginalSnapshotWhenNewTransactionIsCreatedBetweenPageRequests() {
            CheckingAccount account = account();

            Transaction olderTransaction = transactionWithId(
                    TransactionType.DEPOSIT,
                    account,
                    1L
            );

            Transaction boundaryTransaction = transactionWithId(
                    TransactionType.WITHDRAWAL,
                    account,
                    2L
            );

            Pageable firstPageable = PageRequest.of(
                    0,
                    1,
                    PAGEABLE.getSort()
            );

            Pageable nextPageable = PageRequest.of(
                    1,
                    1,
                    PAGEABLE.getSort()
            );

            Page<Transaction> firstPage = new PageImpl<>(
                    List.of(boundaryTransaction),
                    firstPageable,
                    2
            );

            Page<Transaction> secondPage = new PageImpl<>(
                    List.of(olderTransaction),
                    nextPageable,
                    2
            );

            Specification<Transaction> specification = specification();

            stubAccountLookup(account);

            when(transactionRepository.findFirstByAccount_IdOrderByIdDesc(
                    ACCOUNT_ID
            )).thenReturn(Optional.of(boundaryTransaction));

            when(transactionRepository.findByTransactionCodeAndAccount_Id(
                    boundaryTransaction.getTransactionCode(),
                    ACCOUNT_ID
            )).thenReturn(Optional.of(boundaryTransaction));

            when(transactionRepository.findAll(specification, firstPageable))
                    .thenReturn(firstPage);

            when(transactionRepository.findAll(specification, nextPageable))
                    .thenReturn(secondPage);

            try (MockedStatic<TransactionSpecifications> specifications =
                         mockStatic(TransactionSpecifications.class)) {

                stubSpecification(
                        specifications,
                        specification,
                        null,
                        null,
                        null,
                        2L
                );

                TransactionPageResult firstResult =
                        findSnapshotPage(account, firstPageable, null);

                assertEquals(
                        "v1:" + boundaryTransaction.getTransactionCode(),
                        firstResult.snapshotToken()
                );

                assertEquals(
                        List.of(response(boundaryTransaction)),
                        firstResult.page().getContent()
                );

                TransactionPageResult secondResult =
                        findSnapshotPage(
                                account,
                                nextPageable,
                                firstResult.snapshotToken()
                        );

                assertEquals(
                        firstResult.snapshotToken(),
                        secondResult.snapshotToken()
                );

                assertEquals(
                        List.of(response(olderTransaction)),
                        secondResult.page().getContent()
                );

                assertEquals(2, secondResult.page().getTotalElements());

                verifySpecification(
                        specifications,
                        null,
                        null,
                        null,
                        2L,
                        2
                );
            }

            verify(accountService, times(2)).findEntityByAccountNumber(
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT
            );

            verify(transactionRepository).findFirstByAccount_IdOrderByIdDesc(
                    ACCOUNT_ID
            );

            verify(transactionRepository).findByTransactionCodeAndAccount_Id(
                    boundaryTransaction.getTransactionCode(),
                    ACCOUNT_ID
            );

            verify(transactionRepository).findAll(specification, firstPageable);
            verify(transactionRepository).findAll(specification, nextPageable);

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should reject malformed snapshot tokens")
        void shouldRejectMalformedSnapshotTokens() {
            CheckingAccount account = account();

            stubAccountLookup(account);

            for (String invalidSnapshotToken : List.of(
                    "invalid-token",
                    "v1:not-a-valid-uuid"
            )) {
                assertThrowsWithMessage(
                        InvalidTransactionSnapshotException.class,
                        ApiErrorMessages.INVALID_TRANSACTION_SNAPSHOT,
                        () -> findSnapshotPage(
                                account,
                                PAGEABLE,
                                invalidSnapshotToken
                        )
                );
            }

            verify(accountService, times(2)).findEntityByAccountNumber(
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should reject a snapshot token belonging to another account")
        void shouldRejectSnapshotTokenBelongingToAnotherAccount() {
            CheckingAccount account = account();

            UUID transactionCode = UUID.fromString(
                    "123e4567-e89b-12d3-a456-426614174000"
            );

            String snapshotToken = "v1:" + transactionCode;

            stubAccountLookup(account);

            when(transactionRepository.findByTransactionCodeAndAccount_Id(
                    transactionCode,
                    ACCOUNT_ID
            )).thenReturn(Optional.empty());

            assertThrowsWithMessage(
                    InvalidTransactionSnapshotException.class,
                    ApiErrorMessages.INVALID_TRANSACTION_SNAPSHOT,
                    () -> findSnapshotPage(
                            account,
                            PAGEABLE,
                            snapshotToken
                    )
            );

            verifyAccountLookup(account);

            verify(transactionRepository).findByTransactionCodeAndAccount_Id(
                    transactionCode,
                    ACCOUNT_ID
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should reuse the empty snapshot token across page requests")
        void shouldReuseEmptySnapshotTokenAcrossPageRequests() {
            CheckingAccount account = account();

            Pageable nextPageable = PageRequest.of(
                    1,
                    PAGEABLE.getPageSize(),
                    PAGEABLE.getSort()
            );

            Specification<Transaction> specification = specification();

            stubAccountLookup(account);

            when(transactionRepository.findFirstByAccount_IdOrderByIdDesc(
                    ACCOUNT_ID
            )).thenReturn(Optional.empty());

            when(transactionRepository.findAll(specification, PAGEABLE))
                    .thenReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

            when(transactionRepository.findAll(specification, nextPageable))
                    .thenReturn(new PageImpl<>(List.of(), nextPageable, 0));

            try (MockedStatic<TransactionSpecifications> specifications =
                         mockStatic(TransactionSpecifications.class)) {

                stubSpecification(
                        specifications,
                        specification,
                        null,
                        null,
                        null,
                        0L
                );

                TransactionPageResult firstResult =
                        findSnapshotPage(account, PAGEABLE, null);

                assertEquals(
                        "v1:empty:" + account.getAccountNumber() + ":" + account.getDigit(),
                        firstResult.snapshotToken()
                );
                assertTrue(firstResult.page().isEmpty());

                TransactionPageResult secondResult =
                        findSnapshotPage(
                                account,
                                nextPageable,
                                firstResult.snapshotToken()
                        );

                assertEquals(
                        firstResult.snapshotToken(),
                        secondResult.snapshotToken()
                );

                assertTrue(secondResult.page().isEmpty());
                assertEquals(0, secondResult.page().getTotalElements());

                verifySpecification(
                        specifications,
                        null,
                        null,
                        null,
                        0L,
                        2
                );
            }

            verify(accountService, times(2)).findEntityByAccountNumber(
                    ACCOUNT_NUMBER,
                    ACCOUNT_DIGIT
            );

            verify(transactionRepository).findFirstByAccount_IdOrderByIdDesc(
                    ACCOUNT_ID
            );

            verify(transactionRepository).findAll(specification, PAGEABLE);
            verify(transactionRepository).findAll(specification, nextPageable);

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should reject an empty snapshot token belonging to another account")
        void shouldRejectEmptySnapshotTokenBelongingToAnotherAccount() {
            CheckingAccount account = account();

            CheckingAccount otherAccount = new CheckingAccount(
                    null,
                    "00002",
                    "1"
            );

            String snapshotToken = "v1:empty:"
                    + otherAccount.getAccountNumber()
                    + ":"
                    + otherAccount.getDigit();

            stubAccountLookup(account);

            assertThrowsWithMessage(
                    InvalidTransactionSnapshotException.class,
                    ApiErrorMessages.INVALID_TRANSACTION_SNAPSHOT,
                    () -> findSnapshotPage(
                            account,
                            PAGEABLE,
                            snapshotToken
                    )
            );

            verifyAccountLookup(account);
            verifyNoMoreInteractionsOnMocks();
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
            Transaction transaction = Transaction.create(
                    TransactionType.DEPOSIT,
                    OPERATION_AMOUNT,
                    CREATED_AT,
                    account(),
                    TEST_USER,
                    OPERATION_AMOUNT
            );

            when(transactionRepository.findByTransactionCode(
                    transaction.getTransactionCode()
            )).thenReturn(Optional.of(transaction));

            TransactionResponseDTO result =
                    transactionQueryService.findByTransactionCode(
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
                    () -> transactionQueryService.findByTransactionCode(
                            transactionCode
                    )
            );

            verify(transactionRepository).findByTransactionCode(
                    transactionCode
            );

            verifyNoMoreInteractionsOnMocks();
        }
    }

    private void stubAccountLookup(Account account) {
        when(accountService.findEntityByAccountNumber(
                account.getAccountNumber(),
                account.getDigit()
        )).thenReturn(account);
    }

    private void stubLatestTransaction(
            Account account,
            Transaction transaction,
            Long id
    ) {
        ReflectionTestUtils.setField(transaction, "id", id);

        when(transactionRepository.findFirstByAccount_IdOrderByIdDesc(
                account.getId()
        )).thenReturn(Optional.of(transaction));
    }

    private void stubSpecification(
            MockedStatic<TransactionSpecifications> specifications,
            Specification<Transaction> specification,
            TransactionType type,
            Instant startDate,
            Instant endExclusive,
            Long maxTransactionId
    ) {
        specifications.when(() ->
                TransactionSpecifications.byAccountAndFilters(
                        ACCOUNT_ID,
                        type,
                        startDate,
                        endExclusive,
                        maxTransactionId
                )
        ).thenReturn(specification);
    }

    private void verifySpecification(
            MockedStatic<TransactionSpecifications> specifications,
            TransactionType type,
            Instant startDate,
            Instant endExclusive,
            Long maxTransactionId,
            int expectedInvocations
    ) {
        specifications.verify(() ->
                        TransactionSpecifications.byAccountAndFilters(
                                ACCOUNT_ID,
                                type,
                                startDate,
                                endExclusive,
                                maxTransactionId
                        ),
                times(expectedInvocations)
        );
    }

    private Transaction transactionWithId(
            TransactionType type,
            Account account,
            long id
    ) {
        Transaction transaction = Transaction.create(
                type,
                OPERATION_AMOUNT,
                CREATED_AT,
                account,
                TEST_USER,
                OPERATION_AMOUNT
        );

        ReflectionTestUtils.setField(transaction, "id", id);

        return transaction;
    }

    private TransactionPageResult findSnapshotPage(
            Account account,
            Pageable pageable,
            String snapshotToken
    ) {
        return transactionQueryService.findByAccountNumber(
                account.getAccountNumber(),
                account.getDigit(),
                null,
                null,
                null,
                pageable,
                snapshotToken
        );
    }

    private void verifyAccountLookup(Account account) {
        verify(accountService).findEntityByAccountNumber(
                account.getAccountNumber(),
                account.getDigit()
        );
    }

    private void verifyNoMoreInteractionsOnMocks() {
        verifyNoMoreInteractions(
                transactionRepository,
                accountService
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

    private static CheckingAccount account() {
        CheckingAccount account = new CheckingAccount(
                null,
                ACCOUNT_NUMBER,
                ACCOUNT_DIGIT
        );

        ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
        account.credit(new BigDecimal("100.00"));

        return account;
    }
}
