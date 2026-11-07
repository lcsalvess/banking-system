package com.lcsalvess.bankingsystem.unit.entity.account;

import com.lcsalvess.bankingsystem.entity.account.AccountType;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Savings account domain invariants")
class SavingsAccountTests {

    private static final String ACCOUNT_NUMBER = "00001";
    private static final String ACCOUNT_DIGIT = "7";

    private static final LocalDate INITIAL_DATE =
            LocalDate.of(2026, 1, 1);

    private SavingsAccount account() {
        return SavingsAccount.create(
                null,
                ACCOUNT_NUMBER,
                ACCOUNT_DIGIT,
                INITIAL_DATE
        );
    }

    @Test
    @DisplayName("Should create a savings account with initial yield dates")
    void shouldCreateSavingsAccountWithInitialYieldDates() {
        SavingsAccount account = account();

        assertEquals(ACCOUNT_NUMBER, account.getAccountNumber());
        assertEquals(ACCOUNT_DIGIT, account.getDigit());
        assertEquals(AccountType.SAVINGS, account.getType());
        assertEquals(INITIAL_DATE, account.getLastYieldDate());
        assertEquals(INITIAL_DATE, account.getLastYieldProcessedDate());
    }

    @Test
    @DisplayName("Should reject a null initial yield date")
    void shouldRejectNullInitialYieldDate() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SavingsAccount.create(
                        null,
                        ACCOUNT_NUMBER,
                        ACCOUNT_DIGIT,
                        null
                )
        );

        assertEquals(
                "A data inicial do rendimento é obrigatória.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("Should support the JPA protected no-args constructor")
    void shouldSupportJpaNoArgsConstructor() throws ReflectiveOperationException {
        Constructor<SavingsAccount> constructor =
                SavingsAccount.class.getDeclaredConstructor();

        assertTrue(constructor.trySetAccessible());

        SavingsAccount account = constructor.newInstance();

        assertNull(account.getLastYieldDate());
        assertNull(account.getLastYieldProcessedDate());
    }

    @Test
    @DisplayName("Should update the last yield date without moving backwards")
    void shouldUpdateLastYieldDateWithoutMovingBackwards() {
        SavingsAccount account = account();

        account.updateLastYieldDate(INITIAL_DATE);

        assertEquals(INITIAL_DATE, account.getLastYieldDate());

        LocalDate nextDate = INITIAL_DATE.plusDays(1);

        account.updateLastYieldDate(nextDate);

        assertEquals(nextDate, account.getLastYieldDate());
    }

    @Test
    @DisplayName("Should reject moving the last yield date backwards")
    void shouldRejectMovingLastYieldDateBackwards() {
        SavingsAccount account = account();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> account.updateLastYieldDate(INITIAL_DATE.minusDays(1))
        );

        assertEquals(
                "A data do último rendimento não pode retroceder.",
                exception.getMessage()
        );

        assertEquals(INITIAL_DATE, account.getLastYieldDate());
    }

    @Test
    @DisplayName("Should allow updating the last yield date when persisted date is null")
    void shouldAllowUpdatingLastYieldDateWhenPersistedDateIsNull() {
        SavingsAccount account = account();

        // Simulates an uninitialized or inconsistent persisted state
        // to exercise the defensive null branch.
        ReflectionTestUtils.setField(account, "lastYieldDate", null);

        LocalDate referenceDate = INITIAL_DATE.plusDays(1);

        account.updateLastYieldDate(referenceDate);

        assertEquals(referenceDate, account.getLastYieldDate());
    }

    @Test
    @DisplayName("Should reject a null reference date when updating yield date")
    void shouldRejectNullReferenceDateWhenUpdatingYieldDate() {
        SavingsAccount account = account();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> account.updateLastYieldDate(null)
        );

        assertEquals(
                "A data de referência do rendimento é obrigatória.",
                exception.getMessage()
        );

        assertEquals(INITIAL_DATE, account.getLastYieldDate());
    }

    @Test
    @DisplayName("Should determine yield eligibility based on reference date")
    void shouldDetermineYieldEligibilityBasedOnReferenceDate() {
        SavingsAccount account = account();

        assertFalse(account.isEligibleForYield(null));

        assertFalse(
                account.isEligibleForYield(INITIAL_DATE.minusDays(1))
        );

        assertFalse(
                account.isEligibleForYield(INITIAL_DATE)
        );

        assertTrue(
                account.isEligibleForYield(INITIAL_DATE.plusDays(1))
        );
    }

    @Test
    @DisplayName("Should reject yield eligibility when last yield date is null")
    void shouldRejectYieldEligibilityWhenLastYieldDateIsNull() {
        SavingsAccount account = account();

        // Exercises the defensive null branch.
        ReflectionTestUtils.setField(account, "lastYieldDate", null);

        assertFalse(
                account.isEligibleForYield(INITIAL_DATE.plusDays(1))
        );
    }

    @Test
    @DisplayName("Should reject a null historical balance")
    void shouldRejectNullHistoricalBalance() {
        SavingsAccount account = account();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> account.calculateYield(null)
        );

        assertEquals(
                "O saldo histórico deve ser válido e não negativo.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("Should reject a negative historical balance")
    void shouldRejectNegativeHistoricalBalance() {
        SavingsAccount account = account();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> account.calculateYield(new BigDecimal("-0.01"))
        );

        assertEquals(
                "O saldo histórico deve ser válido e não negativo.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("Should calculate daily yield with two decimal places")
    void shouldCalculateDailyYieldWithTwoDecimalPlaces() {
        SavingsAccount account = account();

        assertEquals(
                new BigDecimal("0.17"),
                account.calculateYield(new BigDecimal("1000.00"))
        );

        assertEquals(
                new BigDecimal("0.00"),
                account.calculateYield(new BigDecimal("0.99"))
        );

        assertEquals(
                new BigDecimal("0.00"),
                account.calculateYield(BigDecimal.ZERO)
        );
    }

    @Test
    @DisplayName("Should round daily yield using HALF_UP")
    void shouldRoundDailyYieldUsingHalfUp() {
        SavingsAccount account = account();

        assertEquals(
                new BigDecimal("0.01"),
                account.calculateYield(new BigDecimal("30.08"))
        );
    }

    @Test
    @DisplayName("Should mark yield processing date without moving backwards")
    void shouldMarkYieldProcessingDateWithoutMovingBackwards() {
        SavingsAccount account = account();

        account.markYieldDateProcessed(INITIAL_DATE);

        assertEquals(
                INITIAL_DATE,
                account.getLastYieldProcessedDate()
        );

        LocalDate nextDate = INITIAL_DATE.plusDays(1);

        account.markYieldDateProcessed(nextDate);

        assertEquals(
                nextDate,
                account.getLastYieldProcessedDate()
        );

        // Processing a later date must not modify the last credited yield date.
        assertEquals(INITIAL_DATE, account.getLastYieldDate());
    }

    @Test
    @DisplayName("Should reject moving the processed date backwards")
    void shouldRejectMovingProcessedDateBackwards() {
        SavingsAccount account = account();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> account.markYieldDateProcessed(INITIAL_DATE.minusDays(1))
        );

        assertEquals(
                "A data processada não pode retroceder.",
                exception.getMessage()
        );

        assertEquals(
                INITIAL_DATE,
                account.getLastYieldProcessedDate()
        );
    }

    @Test
    @DisplayName("Should allow marking a processed date when persisted date is null")
    void shouldAllowMarkingProcessedDateWhenPersistedDateIsNull() {
        SavingsAccount account = account();

        // Exercises the defensive null branch.
        ReflectionTestUtils.setField(
                account,
                "lastYieldProcessedDate",
                null
        );

        LocalDate referenceDate = INITIAL_DATE.plusDays(1);

        account.markYieldDateProcessed(referenceDate);

        assertEquals(
                referenceDate,
                account.getLastYieldProcessedDate()
        );
    }

    @Test
    @DisplayName("Should reject a null date when marking yield as processed")
    void shouldRejectNullDateWhenMarkingYieldAsProcessed() {
        SavingsAccount account = account();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> account.markYieldDateProcessed(null)
        );

        assertEquals(
                "A data de processamento do rendimento é obrigatória.",
                exception.getMessage()
        );

        assertEquals(
                INITIAL_DATE,
                account.getLastYieldProcessedDate()
        );
    }
}
