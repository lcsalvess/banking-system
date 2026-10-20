package com.lcsalvess.bankingsystem.unit.validation.passwordstrength;

import com.lcsalvess.bankingsystem.validation.passwordstrength.StrongPasswordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrongPasswordValidatorTests {

    private StrongPasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StrongPasswordValidator();
    }

    @Nested
    class ValidStrongPassword {

        @ParameterizedTest(name = "{0}")
        @MethodSource("validPasswords")
        void shouldAcceptValidPassword(String scenario, String password) {

            assertTrue(validator.isValid(password, null));
        }

        static Stream<Arguments> validPasswords() {
            return Stream.of(
                    Arguments.of(
                            "uppercase, lowercase, digit and exclamation mark",
                            "SecurePass123!"
                    ),
                    Arguments.of(
                            "minimum eight characters with all requirements",
                            "Abcdef1!"
                    ),
                    Arguments.of(
                            "different special character",
                            "StrongPass9@"
                    ),
                    Arguments.of(
                            "special character at the beginning",
                            "!StrongPass9"
                    ),
                    Arguments.of(
                            "special character in the middle",
                            "Strong!Pass9"
                    ),
                    Arguments.of(
                            "contains spaces and a valid special character",
                            "Secure Pass123!"
                    ),
                    Arguments.of(
                            "contains accented letters and a valid special character",
                            "SênhaÁbc123!"
                    ),
                    Arguments.of(
                            "contains a newline and all required character classes",
                            "Secure\nPass123!"
                    ),
                    Arguments.of(
                            "accented uppercase is the only uppercase letter",
                            "áÁ1!áááá"
                    ),
                    Arguments.of(
                            "accented lowercase is the only lowercase letter",
                            "ÁÉÍ1!ÓÚá"
                    ),
                    Arguments.of(
                            "underscore as the special character",
                            "Secure_Pass123"
                    )
            );
        }
    }

    @Nested
    class NullPassword {

        @ParameterizedTest(name = "{0}")
        @MethodSource("nullPasswords")
        void shouldAcceptNullPassword(String scenario, String password) {

            assertTrue(validator.isValid(password, null));
        }

        static Stream<Arguments> nullPasswords() {
            return Stream.of(
                    Arguments.of("null value", null)
            );
        }
    }

    @Nested
    class InvalidStrongPassword {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidPasswords")
        void shouldRejectInvalidPassword(String scenario, String password) {

            assertFalse(validator.isValid(password, null));
        }

        static Stream<Arguments> invalidPasswords() {
            return Stream.of(
                    Arguments.of(
                            "empty value",
                            ""
                    ),
                    Arguments.of(
                            "blank value",
                            "   "
                    ),
                    Arguments.of(
                            "missing uppercase letter",
                            "securepass123!"
                    ),
                    Arguments.of(
                            "missing lowercase letter",
                            "SECUREPASS123!"
                    ),
                    Arguments.of(
                            "missing digit",
                            "SecurePassword!"
                    ),
                    Arguments.of(
                            "missing special character",
                            "SecurePass123"
                    ),
                    Arguments.of(
                            "contains only digits",
                            "12345678"
                    ),
                    Arguments.of(
                            "contains only lowercase letters",
                            "securepassword"
                    ),
                    Arguments.of(
                            "contains only uppercase letters",
                            "SECUREPASSWORD"
                    ),
                    Arguments.of(
                            "contains spaces but no special character",
                            "Secure Pass123"
                    ),
                    Arguments.of(
                            "contains accented letters but no special character",
                            "SênhaAbc123"
                    ),
                    Arguments.of(
                            "control character is not a special character",
                            "SecurePass123\u0001"
                    ),
                    Arguments.of(
                            "tab is not a special character",
                            "Secure\tPass123"
                    )
            );
        }
    }
}