package com.lcsalvess.bankingsystem.unit.service.account.generator;

import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.service.account.generator.AccountNumberGenerator;
import com.lcsalvess.bankingsystem.service.account.generator.GeneratedAccountNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountNumberGeneratorTests {

    @Mock
    private AccountRepository accountRepository;

    private AccountNumberGenerator accountNumberGenerator;

    @BeforeEach
    void setUp() {
        accountNumberGenerator = new AccountNumberGenerator(accountRepository);
    }

    @Test
    @DisplayName("Should generate account number with at least five digits")
    void shouldGenerateAccountNumberWithAtLeastFiveDigits() {
        when(accountRepository.getNextAccountNumber()).thenReturn(1L);

        GeneratedAccountNumber result = accountNumberGenerator.generate();

        assertEquals(new GeneratedAccountNumber("00001", "9"), result);

        verify(accountRepository).getNextAccountNumber();
        verifyNoMoreInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should preserve account numbers longer than five digits")
    void shouldPreserveAccountNumbersLongerThanFiveDigits() {
        when(accountRepository.getNextAccountNumber()).thenReturn(123456L);

        GeneratedAccountNumber result = accountNumberGenerator.generate();

        assertEquals(new GeneratedAccountNumber("123456", "0"), result);

        verify(accountRepository).getNextAccountNumber();
        verifyNoMoreInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should return true for a valid account number and check digit")
    void shouldReturnTrueForValidAccountNumberAndCheckDigit() {
        assertTrue(accountNumberGenerator.isValid("12345", "5"));
    }

    @Test
    @DisplayName("Should accept account numbers longer than five digits")
    void shouldAcceptAccountNumbersLongerThanFiveDigits() {
        assertTrue(accountNumberGenerator.isValid("123456", "0"));
    }

    @Test
    @DisplayName("Should return false when account number is shorter than five digits")
    void shouldReturnFalseWhenAccountNumberIsShorterThanFiveDigits() {
        assertFalse(accountNumberGenerator.isValid("1234", "9"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "12A45",
            "12 45",
            "12.45",
            "١٢٣٤٥",
            "１２３４５"
    })
    @DisplayName("Should reject invalid account numbers")
    void shouldRejectInvalidAccountNumbers(String number) {
        assertFalse(accountNumberGenerator.isValid(number, "5"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "55",
            "A",
            "٥",
            "５"
    })
    @DisplayName("Should reject invalid check digits")
    void shouldRejectInvalidCheckDigits(String digit) {
        assertFalse(accountNumberGenerator.isValid("12345", digit));
    }

    @Test
    @DisplayName("Should return false when check digit is incorrect")
    void shouldReturnFalseWhenCheckDigitIsIncorrect() {
        assertFalse(accountNumberGenerator.isValid("12345", "4"));
    }

    @ParameterizedTest(name = "[{index}] {0}-{1}")
    @CsvSource({
            "12345678, 9",   // 8 digits: the wraparound runs right after the last digit
            "1234567891, 9", // 10 digits: weights restart at 2 after the 8th digit
            "12348, 0"       // remainder 1: raw check digit 10 is normalized to 0
    })
    @DisplayName("Should validate account numbers that exercise weight wraparound and check digit normalization")
    void shouldValidateAccountNumbersThatExerciseWeightWraparound(String number, String digit) {
        assertTrue(accountNumberGenerator.isValid(number, digit));
    }
}
