package com.lcsalvess.bankingsystem.unit.validation.passwordbytelength;

import com.lcsalvess.bankingsystem.validation.passwordbytelength.PasswordByteLengthValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordByteLengthValidatorTests {

    private PasswordByteLengthValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordByteLengthValidator();
    }

    @Test
    void shouldAcceptPasswordWithExactly72AsciiBytes() {
        String password = "a".repeat(72);

        assertTrue(validator.isValid(password, null));
    }

    @Test
    void shouldRejectPasswordWithMoreThan72AsciiBytes() {
        String password = "a".repeat(73);

        assertFalse(validator.isValid(password, null));
    }

    @Test
    void shouldAcceptPasswordWithExactly72Utf8Bytes() {
        String password = "é".repeat(36);

        assertTrue(validator.isValid(password, null));
    }

    @Test
    void shouldRejectPasswordWithMoreThan72Utf8Bytes() {
        String password = "é".repeat(37);

        assertFalse(validator.isValid(password, null));
    }

    @Test
    void shouldAcceptNullValue() {
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void shouldAcceptEmptyPasswordInIsolation() {
        assertTrue(validator.isValid("", null));
    }
}
