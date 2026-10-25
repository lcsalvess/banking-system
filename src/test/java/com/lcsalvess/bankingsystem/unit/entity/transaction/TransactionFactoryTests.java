package com.lcsalvess.bankingsystem.unit.entity.transaction;

import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
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
                    AMOUNT, CREATED_AT, YIELD_DATE, ACCOUNT, BALANCE
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
