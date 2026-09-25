package com.lucas.bankingsystem.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}