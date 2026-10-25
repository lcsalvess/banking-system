package com.lcsalvess.bankingsystem.unit.entity.transaction;

import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transaction factories")
class TransactionFactoryTests {

    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 10, 25, 12, 30, 45);
    private static final LocalDate YIELD_DATE = LocalDate.of(2026, 10, 24);
    private static final BigDecimal AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal BALANCE = new BigDecimal("90.00");
    private static final User USER = new User(
            "employee",
            "employee@example.com",
            "not-a-real-password-hash",
            Role.EMPLOYEE
    );
    private static final CheckingAccount ACCOUNT =
            new CheckingAccount(null, "00001", "0");
    private static final SavingsAccount SAVINGS_ACCOUNT =
            SavingsAccount.create(null, "00002", "0", YIELD_DATE.minusDays(1));

    @Test
    @DisplayName("all factories reject invalid monetary amounts")
    void allFactoriesRejectInvalidAmounts() {
        BigDecimal[] invalidAmounts = {
                BigDecimal.ZERO,
                new BigDecimal("-0.01"),
                new BigDecimal("0.001"),
                new BigDecimal("10000000000000.00")
        };

        for (BigDecimal amount : invalidAmounts) {
            assertThrows(IllegalArgumentException.class, () ->
                    Transaction.create(
                            TransactionType.DEPOSIT,
                            amount,
                            CREATED_AT,
                            ACCOUNT,
                            USER,
                            BALANCE
                    ));

            assertThrows(IllegalArgumentException.class, () ->
                    Transaction.createTransfer(
                            TransactionType.TRANSFER_SENT,
                            amount,
                            CREATED_AT,
                            ACCOUNT,
                            UUID.randomUUID(),
                            USER,
                            BALANCE
                    ));

            assertThrows(IllegalArgumentException.class, () ->
                    Transaction.createSystemYield(
                            amount,
                            CREATED_AT,
                            YIELD_DATE,
                            SAVINGS_ACCOUNT,
                            BALANCE
                    ));
        }
    }

    @Test
    @DisplayName("all factories require creation timestamps and accounts")
    void allFactoriesRequireCreationTimestampAndAccount() {
        assertThrows(NullPointerException.class, () ->
                Transaction.create(
                        TransactionType.DEPOSIT,
                        AMOUNT,
                        null,
                        ACCOUNT,
                        USER,
                        BALANCE
                ));

        assertThrows(NullPointerException.class, () ->
                Transaction.createTransfer(
                        TransactionType.TRANSFER_SENT,
                        AMOUNT,
                        CREATED_AT,
                        null,
                        UUID.randomUUID(),
                        USER,
                        BALANCE
                ));

        assertThrows(NullPointerException.class, () ->
                Transaction.createSystemYield(
                        AMOUNT,
                        null,
                        YIELD_DATE,
                        SAVINGS_ACCOUNT,
                        BALANCE
                ));

        assertThrows(NullPointerException.class, () ->
                Transaction.createSystemYield(
                        AMOUNT,
                        CREATED_AT,
                        YIELD_DATE,
                        null,
                        BALANCE
                ));
    }

    @Test
    @DisplayName("rejects balances that cannot be represented as valid money")
    void rejectsInvalidBalancesAfterTransaction() {
        BigDecimal[] invalidBalances = {
                new BigDecimal("-0.01"),
                new BigDecimal("1.001"),
                new BigDecimal("10000000000000.00")
        };

        for (BigDecimal balance : invalidBalances) {
            assertThrows(IllegalArgumentException.class, () ->
                    Transaction.create(
                            TransactionType.DEPOSIT,
                            AMOUNT,
                            CREATED_AT,
                            ACCOUNT,
                            USER,
                            balance
                    ));
        }
    }

    @Test
    @DisplayName("all factories require an amount")
    void allFactoriesRequireAmount() {
        assertThrows(NullPointerException.class, () ->
                Transaction.create(
                        TransactionType.DEPOSIT,
                        null,
                        CREATED_AT,
                        ACCOUNT,
                        USER,
                        BALANCE
                ));

        assertThrows(NullPointerException.class, () ->
                Transaction.createTransfer(
                        TransactionType.TRANSFER_SENT,
                        null,
                        CREATED_AT,
                        ACCOUNT,
                        UUID.randomUUID(),
                        USER,
                        BALANCE
                ));

        assertThrows(NullPointerException.class, () ->
                Transaction.createSystemYield(
                        null,
                        CREATED_AT,
                        YIELD_DATE,
                        SAVINGS_ACCOUNT,
                        BALANCE
                ));
    }

    @Test
    @DisplayName("system yield transactions require a savings account")
    void systemYieldRequiresSavingsAccount() {
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.createSystemYield(
                        AMOUNT,
                        CREATED_AT,
                        YIELD_DATE,
                        ACCOUNT,
                        BALANCE
                ));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("accepts deposits and withdrawals without transfer metadata")
        void acceptsOrdinaryOperations() {
            for (TransactionType type : new TransactionType[]{
                    TransactionType.DEPOSIT,
                    TransactionType.WITHDRAWAL
            }) {
                Transaction transaction = Transaction.create(
                        type, AMOUNT, CREATED_AT, ACCOUNT, USER, BALANCE
                );

                assertEquals(type, transaction.getType());
                assertNull(transaction.getTransferCode());
                assertNull(transaction.getYieldDate());
                assertFalse(transaction.isPerformedBySystem());
                assertSame(USER, transaction.getPerformedBy());
            }
        }

        @Test
        @DisplayName("rejects transfer and system types")
        void rejectsNonOrdinaryTypes() {
            for (TransactionType type : new TransactionType[]{
                    TransactionType.TRANSFER_SENT,
                    TransactionType.TRANSFER_RECEIVED,
                    TransactionType.YIELD
            }) {
                assertThrows(IllegalArgumentException.class, () ->
                        Transaction.create(
                                type, AMOUNT, CREATED_AT, ACCOUNT, USER, BALANCE
                        ));
            }
        }

        @Test
        @DisplayName("requires a user actor")
        void requiresHumanActor() {
            assertThrows(NullPointerException.class, () ->
                    Transaction.create(
                            TransactionType.DEPOSIT,
                            AMOUNT,
                            CREATED_AT,
                            ACCOUNT,
                            null,
                            BALANCE
                    ));
        }
    }

    @Nested
    @DisplayName("createTransfer")
    class CreateTransfer {

        @Test
        @DisplayName("accepts both transfer directions with a transfer code")
        void acceptsTransferTypes() {
            UUID transferCode = UUID.randomUUID();

            for (TransactionType type : new TransactionType[]{
                    TransactionType.TRANSFER_SENT,
                    TransactionType.TRANSFER_RECEIVED
            }) {
                Transaction transaction = Transaction.createTransfer(
                        type,
                        AMOUNT,
                        CREATED_AT,
                        ACCOUNT,
                        transferCode,
                        USER,
                        BALANCE
                );

                assertEquals(type, transaction.getType());
                assertEquals(transferCode, transaction.getTransferCode());
                assertNull(transaction.getYieldDate());
                assertFalse(transaction.isPerformedBySystem());
                assertSame(USER, transaction.getPerformedBy());
            }
        }

        @Test
        @DisplayName("rejects non-transfer types")
        void rejectsNonTransferTypes() {
            for (TransactionType type : new TransactionType[]{
                    TransactionType.DEPOSIT,
                    TransactionType.WITHDRAWAL,
                    TransactionType.YIELD
            }) {
                assertThrows(IllegalArgumentException.class, () ->
                        Transaction.createTransfer(
                                type,
                                AMOUNT,
                                CREATED_AT,
                                ACCOUNT,
                                UUID.randomUUID(),
                                USER,
                                BALANCE
                        ));
            }
        }

        @Test
        @DisplayName("requires a transfer code")
        void requiresTransferCode() {
            assertThrows(NullPointerException.class, () ->
                    Transaction.createTransfer(
                            TransactionType.TRANSFER_SENT,
                            AMOUNT,
                            CREATED_AT,
                            ACCOUNT,
                            null,
                            USER,
                            BALANCE
                    ));
        }

        @Test
        @DisplayName("requires a user actor")
        void requiresHumanActor() {
            assertThrows(NullPointerException.class, () ->
                    Transaction.createTransfer(
                            TransactionType.TRANSFER_SENT,
                            AMOUNT,
                            CREATED_AT,
                            ACCOUNT,
                            UUID.randomUUID(),
                            null,
                            BALANCE
                    ));
        }
    }

    @Nested
    @DisplayName("createSystemYield")
    class CreateSystemYield {

        @Test
        @DisplayName("creates a system yield with a yield date and no human actor")
        void createsSystemYield() {
            Transaction transaction = Transaction.createSystemYield(
                    AMOUNT, CREATED_AT, YIELD_DATE, SAVINGS_ACCOUNT, BALANCE
            );

            assertEquals(TransactionType.YIELD, transaction.getType());
            assertNull(transaction.getTransferCode());
            assertEquals(YIELD_DATE, transaction.getYieldDate());
            assertTrue(transaction.isPerformedBySystem());
            assertNull(transaction.getPerformedBy());
        }

        @Test
        @DisplayName("requires a yield date")
        void requiresYieldDate() {
            assertThrows(NullPointerException.class, () ->
                    Transaction.createSystemYield(
                            AMOUNT, CREATED_AT, null, ACCOUNT, BALANCE
                    ));
        }
    }
}
