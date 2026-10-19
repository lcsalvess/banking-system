package com.lcsalvess.bankingsystem.unit.service.transaction;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.service.transaction.DailyYieldProcessingResult;
import com.lcsalvess.bankingsystem.service.transaction.DailyYieldScheduler;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyYieldSchedulerTests {

    private static final ZoneId ZONE =
            ZoneId.of("America/Sao_Paulo");

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 1, 15, 0, 5);

    private static final LocalDate TODAY =
            NOW.toLocalDate();

    private static final Clock CLOCK =
            Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionService transactionService;

    private DailyYieldScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DailyYieldScheduler(
                accountRepository,
                transactionService,
                CLOCK
        );
    }

    @Nested
    @DisplayName("Account batch processing")
    class AccountBatchProcessing {

        @Test
        @DisplayName("Should process all active savings accounts in batches")
        void shouldProcessAllSavingsAccountsInBatches() {
            SavingsAccount first = savingsAccount(
                    1L, "00001", "1"
            );

            SavingsAccount second = savingsAccount(
                    2L, "00002", "2"
            );

            givenAccounts(first, second);
            givenYieldResult(first, TODAY, DailyYieldProcessingResult.APPLIED);
            givenYieldResult(second, TODAY, DailyYieldProcessingResult.APPLIED);

            scheduler.processDailyYields();

            verifyBatchLookups(2L);
            verifyYieldCall(first, TODAY);
            verifyYieldCall(second, TODAY);

            verifyNoMoreInteractions(accountRepository, transactionService);
        }

        @Test
        @DisplayName("Should finish when there are no active savings accounts")
        void shouldFinishWhenThereAreNoActiveSavingsAccounts() {
            givenAccounts();

            scheduler.processDailyYields();

            verifyBatchLookups();

            verifyNoMoreInteractions(accountRepository);
            verifyNoInteractions(transactionService);
        }

        @Test
        @DisplayName("Should continue processing when one account fails")
        void shouldContinueProcessingWhenOneAccountFails() {
            SavingsAccount first = savingsAccount(
                    1L, "00001", "1"
            );

            SavingsAccount second = savingsAccount(
                    2L, "00002", "2"
            );

            givenAccounts(first, second);

            when(transactionService.processDailyYieldForDate(
                    first.getAccountNumber(),
                    first.getDigit(),
                    TODAY
            )).thenThrow(
                    new IllegalStateException("Simulated failure")
            );

            givenYieldResult(
                    second,
                    TODAY,
                    DailyYieldProcessingResult.APPLIED
            );

            scheduler.processDailyYields();

            verifyBatchLookups(2L);
            verifyYieldCall(first, TODAY);
            verifyYieldCall(second, TODAY);

            verifyNoMoreInteractions(accountRepository, transactionService);
        }

        @Test
        @DisplayName("Should continue after an account has no yield available")
        void shouldContinueWhenYieldIsNotAvailable() {
            SavingsAccount first = savingsAccount(
                    1L, "00001", "1"
            );

            SavingsAccount second = savingsAccount(
                    2L, "00002", "2"
            );

            givenAccounts(first, second);

            givenYieldResult(
                    first,
                    TODAY,
                    DailyYieldProcessingResult.NO_YIELD_AVAILABLE
            );

            givenYieldResult(
                    second,
                    TODAY,
                    DailyYieldProcessingResult.APPLIED
            );

            scheduler.processDailyYields();

            verifyBatchLookups(2L);
            verifyYieldCall(first, TODAY);
            verifyYieldCall(second, TODAY);

            verifyNoMoreInteractions(accountRepository, transactionService);
        }
    }

    @Nested
    @DisplayName("Infrastructure failure handling")
    class InfrastructureFailureHandling {

        @Test
        @DisplayName("Should log aborted when fetching the next batch fails")
        void shouldLogAbortedWhenFetchingNextBatchFails() {
            SavingsAccount first = savingsAccount(
                    1L, "00001", "1"
            );

            SavingsAccount second = savingsAccount(
                    2L, "00002", "2"
            );

            givenBatchLookupFailure(first, second);

            givenYieldResult(first, TODAY, DailyYieldProcessingResult.APPLIED);
            givenYieldResult(second, TODAY, DailyYieldProcessingResult.APPLIED);

            Logger schedulerLogger =
                    (Logger) LoggerFactory.getLogger(DailyYieldScheduler.class);

            ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
            logAppender.start();
            schedulerLogger.addAppender(logAppender);

            try {
                scheduler.processDailyYields();

                assertTrue(
                        logAppender.list.stream()
                                .map(ILoggingEvent::getFormattedMessage)
                                .anyMatch(message ->
                                        message.contains(
                                                "Daily yield processing aborted for "
                                                        + TODAY
                                        )
                                ),
                        "The scheduler should log that processing was aborted."
                );

                assertTrue(
                        logAppender.list.stream()
                                .map(ILoggingEvent::getFormattedMessage)
                                .anyMatch(message ->
                                        message.contains(
                                                "Daily yield processing stopped while retrieving accounts"
                                        )
                                ),
                        "The scheduler should log the batch retrieval failure."
                );

                verifyBatchLookups(2L);
                verifyYieldCall(first, TODAY);
                verifyYieldCall(second, TODAY);

                verifyNoMoreInteractions(accountRepository, transactionService);

            } finally {
                schedulerLogger.detachAppender(logAppender);
                logAppender.stop();
            }
        }

        @Test
        @DisplayName("Should retry transient infrastructure failures")
        void shouldRetryTransientInfrastructureFailures() {
            SavingsAccount account = savingsAccount(
                    1L, "00001", "1"
            );

            givenAccounts(account);

            when(transactionService.processDailyYieldForDate(
                    account.getAccountNumber(),
                    account.getDigit(),
                    TODAY
            ))
                    .thenThrow(
                            new PessimisticLockingFailureException(
                                    "Simulated temporary lock failure"
                            )
                    )
                    .thenReturn(DailyYieldProcessingResult.APPLIED);

            scheduler.processDailyYields();

            verifyBatchLookups(1L);

            verify(transactionService, times(2)).processDailyYieldForDate(
                    account.getAccountNumber(),
                    account.getDigit(),
                    TODAY
            );

            verifyNoMoreInteractions(accountRepository, transactionService);
        }

        @Test
        @DisplayName("Should continue with other accounts after exhausting retries")
        void shouldContinueWhenRetriesAreExhausted() {
            SavingsAccount first = savingsAccount(
                    1L, "00001", "1"
            );

            SavingsAccount second = savingsAccount(
                    2L, "00002", "2"
            );

            givenAccounts(first, second);

            when(transactionService.processDailyYieldForDate(
                    first.getAccountNumber(),
                    first.getDigit(),
                    TODAY
            )).thenThrow(
                    new PessimisticLockingFailureException(
                            "Temporary failure"
                    )
            );

            givenYieldResult(second, TODAY, DailyYieldProcessingResult.APPLIED);

            scheduler.processDailyYields();

            verifyBatchLookups(2L);

            verify(transactionService, times(3)).processDailyYieldForDate(
                    first.getAccountNumber(),
                    first.getDigit(),
                    TODAY
            );

            verifyYieldCall(second, TODAY);

            verifyNoMoreInteractions(accountRepository, transactionService);
        }
    }

    @Nested
    @DisplayName("Pending date recovery")
    class PendingDateRecovery {

        @Test
        @DisplayName("Should process pending dates in chronological order")
        void shouldProcessPendingDatesInChronologicalOrder() {
            SavingsAccount account = savingsAccount(
                    1L, "00001", "1"
            );

            ReflectionTestUtils.setField(
                    account,
                    "lastYieldProcessedDate",
                    TODAY.minusDays(3)
            );

            givenAccounts(account);

            LocalDate firstDate = TODAY.minusDays(2);
            LocalDate secondDate = TODAY.minusDays(1);

            givenYieldResult(
                    account,
                    firstDate,
                    DailyYieldProcessingResult.APPLIED
            );

            givenYieldResult(
                    account,
                    secondDate,
                    DailyYieldProcessingResult.APPLIED
            );

            givenYieldResult(
                    account,
                    TODAY,
                    DailyYieldProcessingResult.APPLIED
            );

            scheduler.processDailyYields();

            InOrder inOrder = inOrder(transactionService);

            inOrder.verify(transactionService).processDailyYieldForDate(
                    account.getAccountNumber(),
                    account.getDigit(),
                    firstDate
            );

            inOrder.verify(transactionService).processDailyYieldForDate(
                    account.getAccountNumber(),
                    account.getDigit(),
                    secondDate
            );

            inOrder.verify(transactionService).processDailyYieldForDate(
                    account.getAccountNumber(),
                    account.getDigit(),
                    TODAY
            );

            verifyBatchLookups(1L);

            verifyNoMoreInteractions(accountRepository, transactionService);
        }
    }

    private void givenFirstBatch(SavingsAccount... accounts) {
        when(accountRepository.findNextSavingsAccountsByStatus(
                eq(AccountStatus.ACTIVE),
                eq(0L),
                any(Pageable.class)
        )).thenReturn(List.of(accounts));
    }

    private void givenAccounts(SavingsAccount... accounts) {
        givenFirstBatch(accounts);

        if (accounts.length == 0) {
            return;
        }

        long lastId = accounts[accounts.length - 1].getId();

        when(accountRepository.findNextSavingsAccountsByStatus(
                eq(AccountStatus.ACTIVE),
                eq(lastId),
                any(Pageable.class)
        )).thenReturn(List.of());
    }

    private void givenBatchLookupFailure(SavingsAccount... accounts) {
        givenFirstBatch(accounts);

        long lastId = accounts[accounts.length - 1].getId();

        when(accountRepository.findNextSavingsAccountsByStatus(
                eq(AccountStatus.ACTIVE),
                eq(lastId),
                any(Pageable.class)
        )).thenThrow(
                new IllegalStateException("Simulated batch lookup failure")
        );
    }

    private void givenYieldResult(
            SavingsAccount account,
            LocalDate referenceDate,
            DailyYieldProcessingResult result
    ) {
        when(transactionService.processDailyYieldForDate(
                account.getAccountNumber(),
                account.getDigit(),
                referenceDate
        )).thenReturn(result);
    }

    private void verifyYieldCall(
            SavingsAccount account,
            LocalDate referenceDate
    ) {
        verify(transactionService).processDailyYieldForDate(
                account.getAccountNumber(),
                account.getDigit(),
                referenceDate
        );
    }

    private void verifyBatchLookups(long... afterIds) {
        verify(accountRepository).findNextSavingsAccountsByStatus(
                AccountStatus.ACTIVE,
                0L,
                PageRequest.of(0, 100)
        );

        for (long afterId : afterIds) {
            verify(accountRepository).findNextSavingsAccountsByStatus(
                    AccountStatus.ACTIVE,
                    afterId,
                    PageRequest.of(0, 100)
            );
        }
    }

    private static SavingsAccount savingsAccount(
            Long id,
            String accountNumber,
            String digit
    ) {
        SavingsAccount account = new SavingsAccount(
                null,
                accountNumber,
                digit,
                TODAY.minusDays(1)
        );

        ReflectionTestUtils.setField(account, "id", id);

        return account;
    }
}