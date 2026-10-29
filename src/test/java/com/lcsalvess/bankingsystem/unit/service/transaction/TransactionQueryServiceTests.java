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
import com.lcsalvess.bankingsystem.exception.transaction.TransactionNotFoundException;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.specification.TransactionSpecifications;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.transaction.TransactionQueryService;
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
                        )
                ).thenReturn(specification);

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

                specifications.verify(() ->
                        TransactionSpecifications.byAccountAndFilters(
                                ACCOUNT_ID,
                                null,
                                null,
                                null
                        )
                );
            }

            verifyAccountLookup(account);
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

                specifications.when(() ->
                        TransactionSpecifications.byAccountAndFilters(
                                ACCOUNT_ID,
                                TransactionType.DEPOSIT,
                                expectedStart,
                                expectedEndExclusive
                        )
                ).thenReturn(specification);

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

                specifications.verify(() ->
                        TransactionSpecifications.byAccountAndFilters(
                                ACCOUNT_ID,
                                TransactionType.DEPOSIT,
                                expectedStart,
                                expectedEndExclusive
                        )
                );

                verify(transactionRepository).findAll(
                        specification,
                        PAGEABLE
                );
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
                        )
                ).thenReturn(specification);

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
                        )
                ).thenReturn(specification);

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
