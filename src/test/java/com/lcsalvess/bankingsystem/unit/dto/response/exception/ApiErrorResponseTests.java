package com.lcsalvess.bankingsystem.unit.dto.response.exception;

import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ApiErrorResponse")
class ApiErrorResponseTests {

    @Nested
    @DisplayName("Error ordering")
    class ErrorOrderingTests {

        @Test
        @DisplayName("Should preserve the insertion order of field errors")
        void shouldPreserveInsertionOrder() {
            Map<String, String> errors = new LinkedHashMap<>();
            errors.put("username", "Username is required.");
            errors.put("email", "Invalid email address.");
            errors.put("cpf", "Invalid CPF.");
            errors.put("password", "Password is too weak.");

            ApiErrorResponse response = new ApiErrorResponse(
                    400,
                    "Request validation failed.",
                    errors
            );

            assertEquals(
                    List.of("username", "email", "cpf", "password"),
                    new ArrayList<>(response.errors().keySet())
            );
        }
    }

    @Nested
    @DisplayName("Defensive copying")
    class DefensiveCopyTests {

        @Test
        @DisplayName("Should not be affected by later changes to the source map")
        void shouldCopyErrorsDefensively() {
            Map<String, String> errors = new LinkedHashMap<>();
            errors.put("email", "Invalid email address.");

            ApiErrorResponse response = new ApiErrorResponse(
                    400,
                    "Request validation failed.",
                    errors
            );

            errors.put("username", "Username is required.");

            assertEquals(1, response.errors().size());
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("Should expose an immutable errors map")
        void shouldExposeImmutableErrors() {
            ApiErrorResponse response = new ApiErrorResponse(
                    400,
                    "Request validation failed.",
                    Map.of("email", "Invalid email address.")
            );

            Map<String, String> exposed = response.errors();

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> exposed.put("username", "Username is required.")
            );
        }
    }

    @Nested
    @DisplayName("Null errors")
    class NullErrorsTests {

        @Test
        @DisplayName("Should keep errors null when not applicable")
        void shouldKeepNullErrors() {
            ApiErrorResponse response = new ApiErrorResponse(
                    404,
                    "Not found."
            );

            assertNull(response.errors());
        }
    }
}