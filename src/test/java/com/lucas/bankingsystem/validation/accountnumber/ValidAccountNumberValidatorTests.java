package com.lucas.bankingsystem.validation.accountnumber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidAccountNumberValidatorTests {

    private ValidAccountNumberValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ValidAccountNumberValidator();
    }

    @Nested
    class ValidAccountNumber {

        @ParameterizedTest(name = "{0}")
        @MethodSource("validAccountNumbers")
        void shouldAcceptValidAccountNumber(String scenario, String accountNumber) {

            assertTrue(validator.isValid(accountNumber, null));
        }

        static Stream<Arguments> validAccountNumbers() {
            return Stream.of(
                    Arguments.of("five digits", "12345"),
                    Arguments.of("six digits", "123456"),
                    Arguments.of("twenty digits", "12345678901234567890"),
                    Arguments.of("five zeros", "00000"),
                    Arguments.of("leading zeros", "00001")
            );
        }
    }

    @Nested
    class NullAccountNumber {

        @ParameterizedTest(name = "{0}")
        @MethodSource("nullAccountNumbers")
        void shouldAcceptNullAccountNumber(String scenario, String accountNumber) {

            assertTrue(validator.isValid(accountNumber, null));
        }

        static Stream<Arguments> nullAccountNumbers() {
            return Stream.of(
                    Arguments.of("null value", null)
            );
        }
    }

    @Nested
    class InvalidAccountNumber {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidAccountNumbers")
        void shouldRejectInvalidAccountNumber(String scenario, String accountNumber) {

            assertFalse(validator.isValid(accountNumber, null));
        }

        static Stream<Arguments> invalidAccountNumbers() {
            return Stream.of(
                    Arguments.of("empty value", ""),
                    Arguments.of("blank value", "   "),
                    Arguments.of("less than five digits", "1234"),
                    Arguments.of("more than twenty digits", "123456789012345678901"),
                    Arguments.of("contains letters", "1234A"),
                    Arguments.of("contains special characters", "123-5")
            );
        }
    }
}