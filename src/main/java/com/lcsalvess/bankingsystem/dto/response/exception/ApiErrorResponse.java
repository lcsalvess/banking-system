package com.lcsalvess.bankingsystem.dto.response.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response returned by the API.")
public record ApiErrorResponse(
        @Schema(
                description = "HTTP status code.",
                example = "400"
        )
        int status,

        @Schema(
                description = "Description of the error.",
                example = "Request validation failed."
        )
        String message,

        @Schema(
                description = "Date and time when the error occurred, in UTC.",
                example = "2026-10-15T18:30:00Z"
        )
        Instant timestamp,

        @Schema(
                description = "Validation errors associated with individual fields. Omitted when not applicable.",
                example = "{\"email\":\"Invalid email address.\",\"username\":\"Username is required.\"}",
                nullable = true
        )
        Map<String, String> errors
) {

    public ApiErrorResponse {
        errors = errors == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    /**
     * Creates an error response stamped with the application clock.
     */
    public static ApiErrorResponse of(
            Clock clock,
            int status,
            String message
    ) {
        return new ApiErrorResponse(status, message, clock.instant(), null);
    }

    /**
     * Creates a validation error response stamped with the application clock.
     */
    public static ApiErrorResponse of(
            Clock clock,
            int status,
            String message,
            Map<String, String> errors
    ) {
        return new ApiErrorResponse(status, message, clock.instant(), errors);
    }
}
