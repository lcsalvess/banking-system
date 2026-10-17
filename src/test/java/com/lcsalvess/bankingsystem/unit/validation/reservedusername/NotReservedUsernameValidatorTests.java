package com.lcsalvess.bankingsystem.unit.validation.reservedusername;

import com.lcsalvess.bankingsystem.validation.reservedusername.NotReservedUsernameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotReservedUsernameValidatorTests {

    private NotReservedUsernameValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NotReservedUsernameValidator();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"system", "SYSTEM", "System", "sYsTeM", " system "})
    void shouldRejectReservedUsernameRegardlessOfCaseAndSurroundingSpaces(
            String username) {

        assertFalse(validator.isValid(username, null));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"systems", "system1", "system-admin", "my.system", "sys", "admin"})
    void shouldAcceptUsernamesThatOnlyContainTheReservedName(String username) {
        assertTrue(validator.isValid(username, null));
    }

    @Test
    void shouldAcceptNullValue() {
        assertTrue(validator.isValid(null, null));
    }
}