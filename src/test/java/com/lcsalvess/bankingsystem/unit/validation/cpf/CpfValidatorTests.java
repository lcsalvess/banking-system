package com.lcsalvess.bankingsystem.unit.validation.cpf;

import com.lcsalvess.bankingsystem.validation.cpf.CpfValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpfValidatorTests {

    private final CpfValidator cpfValidator = new CpfValidator();

    @Test
    @DisplayName("Should return true for a valid CPF")
    void shouldReturnTrueForValidCpf() {
        assertTrue(cpfValidator.isValid("52998224725"));
    }

    @Test
    @DisplayName("Should return false when CPF is null")
    void shouldReturnFalseWhenCpfIsNull() {
        assertFalse(cpfValidator.isValid(null));
    }

    @Test
    @DisplayName("Should return false when CPF has less than 11 digits")
    void shouldReturnFalseWhenCpfHasLessThanElevenDigits() {
        assertFalse(cpfValidator.isValid("5299822472"));
    }

    @Test
    @DisplayName("Should return false when CPF has more than 11 digits")
    void shouldReturnFalseWhenCpfHasMoreThanElevenDigits() {
        assertFalse(cpfValidator.isValid("529982247250"));
    }

    @Test
    @DisplayName("Should return false when CPF contains non-numeric characters")
    void shouldReturnFalseWhenCpfContainsNonNumericCharacters() {
        assertFalse(cpfValidator.isValid("5299822472A"));
    }

    @Test
    @DisplayName("Should return false when all CPF digits are equal")
    void shouldReturnFalseWhenAllCpfDigitsAreEqual() {
        assertFalse(cpfValidator.isValid("11111111111"));
    }

    @Test
    @DisplayName("Should return false when first check digit is invalid")
    void shouldReturnFalseWhenFirstCheckDigitIsInvalid() {
        assertFalse(cpfValidator.isValid("52998224735"));
    }

    @Test
    @DisplayName("Should return false when second check digit is invalid")
    void shouldReturnFalseWhenSecondCheckDigitIsInvalid() {
        assertFalse(cpfValidator.isValid("52998224724"));
    }

    @Test
    @DisplayName("Should return false when CPF contains spaces")
    void shouldReturnFalseWhenCpfContainsSpaces() {
        assertFalse(cpfValidator.isValid("5299822472 "));
    }

    @Test
    @DisplayName("Should return false when CPF contains punctuation")
    void shouldReturnFalseWhenCpfContainsPunctuation() {
        assertFalse(cpfValidator.isValid("5299822472."));
    }

    @Test
    @DisplayName("Should return false when CPF contains Arabic-Indic digits")
    void shouldReturnFalseWhenCpfContainsArabicIndicDigits() {
        assertFalse(cpfValidator.isValid("٥" + "2998224725"));
    }

    @Test
    @DisplayName("Should return false when CPF contains fullwidth digits")
    void shouldReturnFalseWhenCpfContainsFullwidthDigits() {
        assertFalse(cpfValidator.isValid("５" + "2998224725"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "12345685000", // 1st digit: remainder 0 -> 0 | 2nd digit: remainder 1 -> 0
            "12345698500", // 1st digit: remainder 1 -> 0 | 2nd digit: remainder 0 -> 0
            "12345685190", // 1st digit: remainder 2 -> 9 (boundary) | 2nd digit: remainder 0 -> 0
            "12345683309"  // 1st digit: remainder 0 -> 0 | 2nd digit: remainder 2 -> 9 (boundary)
    })
    @DisplayName("Should return true for valid CPFs whose check digit remainders are 0, 1 and 2")
    void shouldReturnTrueWhenCheckDigitRemaindersAreOnTheBoundary(String cpf) {
        assertTrue(cpfValidator.isValid(cpf));
    }

    @Test
    @DisplayName("Should return false when first check digit should be zero but is not")
    void shouldReturnFalseWhenFirstCheckDigitShouldBeZeroButIsNot() {
        // base 123456797 has remainder 0, so the correct 1st digit is 0
        assertFalse(cpfValidator.isValid("12345679710"));
    }

    @Test
    @DisplayName("Should return false when second check digit should be zero but is not")
    void shouldReturnFalseWhenSecondCheckDigitShouldBeZeroButIsNot() {
        // 1st digit is correct (0); the 2nd remainder is 0, so the correct 2nd digit is 0
        assertFalse(cpfValidator.isValid("12345679701"));
    }
}
