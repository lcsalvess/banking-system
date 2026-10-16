package com.lcsalvess.bankingsystem.unit.service.transaction;

import com.lcsalvess.bankingsystem.entity.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.enums.AccountStatus;
import com.lcsalvess.bankingsystem.exception.transaction.YieldNotAvailableException;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.service.transaction.DailyYieldScheduler;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Test
    @DisplayName("Should process all active savings accounts in batches")
    void shouldProcessAllSavingsAccountsInBatches() {
        SavingsAccount first = savingsAccount(
                1L, "00001", "1"
        );

        SavingsAccount second = savingsAccount(
                2L, "00002", "2"
        );

        when(accountRepository.findNextSavingsAccountsByStatus(
                eq(AccountStatus.ACTIVE),
                eq(0L),
                any(Pageable.class)
        )).thenReturn(List.of(first, second));

        when(accountRepository.findNextSavingsAccountsByStatus(
                eq(AccountStatus.ACTIVE),
                eq(2L),
                any(Pageable.class)
        )).thenReturn(List.of());

        scheduler.processDailyYields();

        verify(accountRepository).findNextSavingsAccountsByStatus(
                AccountStatus.ACTIVE,
                0L,
                PageRequest.of(0, 100)
        );

        verify(accountRepository).findNextSavingsAccountsByStatus(
                AccountStatus.ACTIVE,
                2L,
                PageRequest.of(0, 100)
        );

        verify(transactionService).applyYieldForDate(
                "00001", "1", TODAY
        );

        verify(transactionService).applyYieldForDate(
                "00002", "2", TODAY
        );

        verifyNoMoreInteractions(transactionService);
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

        when(accountRepository.findNextSavingsAccountsByStatus(
                eq(AccountStatus.ACTIVE),
                eq(0L),
                any(Pageable.class)
        )).thenReturn(List.of(first, second));

        when(accountRepository.findNextSavingsAccountsByStatus(
                eq(AccountStatus.ACTIVE),
                eq(2L),
                any(Pageable.class)
        )).thenReturn(List.of());

        when(transactionService.applyYieldForDate(
                "00001", "1", TODAY
        )).thenThrow(new IllegalStateException("Simulated failure"));

        assertDoesNotThrow(() -> scheduler.processDailyYields());

        verify(transactionService).applyYieldForDate(
                "00001", "1", TODAY
        );

        verify(transactionService).applyYieldForDate(
                "00002", "2", TODAY
        );

        verifyNoMoreInteractions(transactionService);
    }

    @Test
    @DisplayName("Should finish when there are no active savings accounts")
    void shouldFinishWhenThereAreNoActiveSavingsAccounts() {
        when(accountRepository.findNextSavingsAccountsByStatus(
                eq(AccountStatus.ACTIVE),
                eq(0L),
                any(Pageable.class)
        )).thenReturn(List.of());

        assertDoesNotThrow(() -> scheduler.processDailyYields());

        verify(accountRepository).findNextSavingsAccountsByStatus(
                AccountStatus.ACTIVE,
                0L,
                PageRequest.of(0, 100)
        );

        verifyNoInteractions(transactionService);
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

        when(accountRepository.findNextSavingsAccountsByStatus(
                eq(AccountStatus.ACTIVE),
                eq(0L),
                any(Pageable.class)
        )).thenReturn(List.of(first, second));

        when(accountRepository.findNextSavingsAccountsByStatus(
                eq(AccountStatus.ACTIVE),
                eq(2L),
                any(Pageable.class)
        )).thenReturn(List.of());

        when(transactionService.applyYieldForDate(
                "00001", "1", TODAY
        )).thenThrow(
                new YieldNotAvailableException(
                        "Rendimento diário indisponível."
                )
        );

        assertDoesNotThrow(() -> scheduler.processDailyYields());

        verify(transactionService).applyYieldForDate(
                "00001", "1", TODAY
        );

        verify(transactionService).applyYieldForDate(
                "00002", "2", TODAY
        );

        verifyNoMoreInteractions(transactionService);
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
