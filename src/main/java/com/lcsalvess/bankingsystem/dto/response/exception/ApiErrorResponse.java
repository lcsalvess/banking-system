package com.lcsalvess.bankingsystem.dto.response.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
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
        errors = errors == null ? null : Map.copyOf(errors);
    }

    public ApiErrorResponse(int status, String message) {
        this(status, message, Instant.now(), null);
    }

    public ApiErrorResponse(
            int status,
            String message,
            Map<String, String> errors
    ) {
        this(status, message, Instant.now(), errors);
    }
}