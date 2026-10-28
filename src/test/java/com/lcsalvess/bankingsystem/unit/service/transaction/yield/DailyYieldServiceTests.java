package com.lcsalvess.bankingsystem.unit.service.transaction.yield;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.event.transaction.TransactionOperationEvent;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotActiveException;
import com.lcsalvess.bankingsystem.exception.account.AccountIsNotSavingsException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.transaction.yield.DailyYieldProcessingResult;
import com.lcsalvess.bankingsystem.service.transaction.yield.DailyYieldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyYieldServiceTests {

    private static final String ACCOUNT_NUMBER = "00001";
    private static final String ACCOUNT_DIGIT = "9";
    private static final Long ACCOUNT_ID = 1L;
    private static final String SAVINGS_NUMBER = "00002";
    private static final String SAVINGS_DIGIT = "7";
    private static final Long SAVINGS_ID = 2L;

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 23, 30);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    private static final Instant DAY_START = TODAY.atStartOfDay(ZONE).toInstant();

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DailyYieldService dailyYieldService;

    @BeforeEach
    void setUp() {
        dailyYieldService = new DailyYieldService(
                transactionRepository,
                accountService,
                eventPublisher,
                CLOCK
        );
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
                    DAY_START
            )).thenReturn(new BigDecimal("1000.00"));

            stubSaveReturningArgument();

            DailyYieldProcessingResult result =
                    dailyYieldService.processDailyYieldForDate(
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
            verify(transactionRepository).findBalanceAtStartOfDayFromSnapshot(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
            );
            verify(transactionRepository).calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
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
                    DAY_START
            )).thenReturn(new BigDecimal("40.00"));

            stubSaveReturningArgument();

            DailyYieldProcessingResult result =
                    dailyYieldService.processDailyYieldForDate(
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
            verify(transactionRepository).findBalanceAtStartOfDayFromSnapshot(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
            );
            verify(transactionRepository).calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
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
                    () -> dailyYieldService.processDailyYieldForDate(
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
            ReflectionTestUtils.setField(account, "status", AccountStatus.CANCELLED);

            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    AccountIsNotActiveException.class,
                    ApiErrorMessages.ACCOUNT_NOT_ACTIVE,
                    () -> dailyYieldService.processDailyYieldForDate(
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
                    dailyYieldService.processDailyYieldForDate(
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
                    () -> dailyYieldService.processDailyYieldForDate(
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
                    DAY_START
            )).thenReturn(new BigDecimal("0.99"));

            DailyYieldProcessingResult result =
                    dailyYieldService.processDailyYieldForDate(
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
            verify(transactionRepository).findBalanceAtStartOfDayFromSnapshot(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
            );
            verify(transactionRepository).calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
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
                    DAY_START
            )).thenReturn(Account.MAX_BALANCE);

            DailyYieldProcessingResult result =
                    dailyYieldService.processDailyYieldForDate(
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
            verify(transactionRepository).findBalanceAtStartOfDayFromSnapshot(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
            );
            verify(transactionRepository).calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should reject a null reference date")
        void shouldRejectNullReferenceDate() {
            assertThrowsWithMessage(
                    IllegalArgumentException.class,
                    "A data de referência do rendimento é obrigatória.",
                    () -> dailyYieldService.processDailyYieldForDate(
                            SAVINGS_NUMBER,
                            SAVINGS_DIGIT,
                            null
                    )
            );

            verifyNoInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should reject a reference date that skips a pending day")
        void shouldRejectReferenceDateThatSkipsPendingDay() {
            SavingsAccount account = eligibleSavingsAccount("1000.00");
            ReflectionTestUtils.setField(account, "lastYieldProcessedDate", TODAY.minusDays(2));
            stubAccountOperationLookup(account);

            assertThrowsWithMessage(
                    IllegalStateException.class,
                    "As datas de rendimento devem ser processadas em ordem.",
                    () -> dailyYieldService.processDailyYieldForDate(
                            SAVINGS_NUMBER,
                            SAVINGS_DIGIT,
                            TODAY
                    )
            );

            verifyAccountOperationLookup(account);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should use the start-of-day snapshot when available")
        void shouldUseStartOfDaySnapshotWhenAvailable() {
            SavingsAccount account = eligibleSavingsAccount("1000.00");
            stubAccountOperationLookup(account);

            when(transactionRepository.existsByAccountIdAndTypeAndYieldDate(
                    SAVINGS_ID,
                    TransactionType.YIELD,
                    TODAY
            )).thenReturn(false);

            when(transactionRepository.findBalanceAtStartOfDayFromSnapshot(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
            )).thenReturn(Optional.of(new BigDecimal("1000.00")));

            stubSaveReturningArgument();

            DailyYieldProcessingResult result =
                    dailyYieldService.processDailyYieldForDate(
                            SAVINGS_NUMBER,
                            SAVINGS_DIGIT,
                            TODAY
                    );

            assertEquals(DailyYieldProcessingResult.APPLIED, result);
            assertBalance("1000.17", account);
            verify(transactionRepository).findBalanceAtStartOfDayFromSnapshot(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
            );
            verify(transactionRepository, never()).calculateBalanceAtStartOfDay(
                    SAVINGS_ID,
                    TODAY,
                    DAY_START
            );
            verify(transactionRepository).save(any(Transaction.class));
            verifyAccountOperationLookup(account);
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

    }

    private void stubAccountOperationLookup(Account account) {
        when(accountService.findEntityByAccountNumberForUpdate(
                account.getAccountNumber(),
                account.getDigit()
        )).thenReturn(account);
    }

    private void verifyAccountOperationLookup(Account account) {
        verify(accountService).findEntityByAccountNumberForUpdate(
                account.getAccountNumber(),
                account.getDigit()
        );
    }

    private void stubSaveReturningArgument() {
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void verifyNoInteractionsOnMocks() {
        verifyNoInteractions(transactionRepository, accountService, eventPublisher);
    }

    private void verifyNoMoreInteractionsOnMocks() {
        verifyNoMoreInteractions(transactionRepository, accountService, eventPublisher);
    }

    private static void assertThrowsWithMessage(
            Class<? extends Exception> type,
            String message,
            Executable executable
    ) {
        Exception exception = assertThrows(type, executable);
        assertEquals(message, exception.getMessage());
    }

    private static void assertBalance(String expected, Account account) {
        assertEquals(0, new BigDecimal(expected).compareTo(account.getBalance()));
    }

    private static CheckingAccount account() {
        return checkingAccount(ACCOUNT_NUMBER, ACCOUNT_DIGIT, ACCOUNT_ID, "100.00");
    }

    private static CheckingAccount checkingAccount(
            String number,
            String digit,
            Long id,
            String balance
    ) {
        CheckingAccount account = new CheckingAccount(null, number, digit);
        ReflectionTestUtils.setField(account, "id", id);
        account.credit(new BigDecimal(balance));
        return account;
    }

    private static SavingsAccount savingsAccount(String balance, LocalDate lastYieldDate) {
        SavingsAccount account = SavingsAccount.create(
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
        return savingsAccount(balance, TODAY.minusDays(1));
    }
}
