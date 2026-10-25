package com.lcsalvess.bankingsystem.unit.entity.account;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Account domain invariants")
class AccountTests {

    private static final BigDecimal TEN = new BigDecimal("10.00");

    private CheckingAccount account() {
        return new CheckingAccount(null, "00001", "0");
    }

    @Test
    @DisplayName("starts with a zero balance")
    void startsWithZeroBalance() {
        Account account = account();

        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
    }

    @Test
    @DisplayName("credits a valid positive amount")
    void creditsValidAmount() {
        Account account = account();

        account.credit(TEN);

        assertEquals(TEN, account.getBalance());
        assertTrue(account.canCredit(new BigDecimal("5.00")));
    }

    @Test
    @DisplayName("rejects invalid credit amounts")
    void rejectsInvalidCreditAmounts() {
        Account account = account();
        BigDecimal[] invalidAmounts = {
                BigDecimal.ZERO,
                new BigDecimal("-0.01"),
                new BigDecimal("0.001"),
                new BigDecimal("10000000000000.00")
        };

        for (BigDecimal amount : invalidAmounts) {
            assertThrows(IllegalArgumentException.class,
                    () -> account.credit(amount));
            assertFalse(account.canCredit(amount));
        }

        assertThrows(NullPointerException.class, () -> account.credit(null));
        assertFalse(account.canCredit(null));
        assertEquals(BigDecimal.ZERO, account.getBalance());
    }

    @Test
    @DisplayName("rejects a credit that would exceed the maximum balance")
    void rejectsCreditAboveMaximumBalance() {
        Account account = account();

        account.credit(Account.MAX_BALANCE);

        assertFalse(account.canCredit(new BigDecimal("0.01")));
        assertThrows(IllegalStateException.class,
                () -> account.credit(new BigDecimal("0.01")));
        assertEquals(Account.MAX_BALANCE, account.getBalance());
    }

    @Test
    @DisplayName("debits valid amounts without allowing a negative balance")
    void debitsWithoutOverdraft() {
        Account account = account();
        account.credit(TEN);

        account.debit(new BigDecimal("3.50"));

        assertEquals(new BigDecimal("6.50"), account.getBalance());
        assertThrows(IllegalStateException.class,
                () -> account.debit(new BigDecimal("6.51")));
        assertEquals(new BigDecimal("6.50"), account.getBalance());
    }

    @Test
    @DisplayName("rejects invalid debit amounts")
    void rejectsInvalidDebitAmounts() {
        Account account = account();
        account.credit(TEN);

        assertThrows(IllegalArgumentException.class,
                () -> account.debit(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> account.debit(new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class,
                () -> account.debit(new BigDecimal("1.001")));
        assertThrows(NullPointerException.class, () -> account.debit(null));

        assertEquals(TEN, account.getBalance());
    }

    @Test
    @DisplayName("cancels only an active account with a zero balance")
    void cancellationRequiresZeroBalanceAndActiveStatus() {
        Account account = account();
        account.credit(TEN);

        assertThrows(IllegalStateException.class, account::cancel);
        assertEquals(AccountStatus.ACTIVE, account.getStatus());

        account.debit(TEN);
        account.cancel();

        assertEquals(AccountStatus.CANCELLED, account.getStatus());
        assertThrows(IllegalStateException.class, account::cancel);
    }

    @Test
    @DisplayName("does not allow balance operations after cancellation")
    void rejectsBalanceOperationsAfterCancellation() {
        Account account = account();
        account.cancel();

        assertFalse(account.canCredit(new BigDecimal("1.00")));
        assertThrows(IllegalStateException.class,
                () -> account.credit(new BigDecimal("1.00")));
        assertThrows(IllegalStateException.class,
                () -> account.debit(new BigDecimal("0.01")));
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals(AccountStatus.CANCELLED, account.getStatus());
    }
}
