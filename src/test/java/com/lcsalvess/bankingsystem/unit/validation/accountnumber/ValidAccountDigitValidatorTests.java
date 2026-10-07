package com.lcsalvess.bankingsystem.unit.validation.accountnumber;

import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountDigitValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidAccountDigitValidatorTests {

    private ValidAccountDigitValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ValidAccountDigitValidator();
    }

    @Nested
    class ValidAccountDigit {

        @ParameterizedTest(name = "{0}")
        @MethodSource("validAccountDigits")
        void shouldAcceptValidAccountDigit(String scenario, String digit) {

            assertTrue(validator.isValid(digit, null));
        }

        static Stream<Arguments> validAccountDigits() {
            return Stream.of(
                    Arguments.of("zero", "0"),
                    Arguments.of("middle digit", "5"),
                    Arguments.of("maximum digit", "9")
            );
        }
    }

    @Nested
    class NullAccountDigit {

        @ParameterizedTest(name = "{0}")
        @MethodSource("nullAccountDigits")
        void shouldAcceptNullAccountDigit(String scenario, String digit) {

            assertTrue(validator.isValid(digit, null));
        }

        static Stream<Arguments> nullAccountDigits() {
            return Stream.of(
                    Arguments.of("null value", null)
            );
        }
    }

    @Nested
    class InvalidAccountDigit {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidAccountDigits")
        void shouldRejectInvalidAccountDigit(String scenario, String digit) {

            assertFalse(validator.isValid(digit, null));
        }

        static Stream<Arguments> invalidAccountDigits() {
            return Stream.of(
                    Arguments.of("empty value", ""),
                    Arguments.of("blank value", " "),
                    Arguments.of("more than one digit", "12"),
                    Arguments.of("contains letters", "A"),
                    Arguments.of("contains special characters", "-")
            );
        }
    }
}