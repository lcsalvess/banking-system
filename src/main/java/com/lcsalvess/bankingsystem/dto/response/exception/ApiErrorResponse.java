package com.lcsalvess.bankingsystem.dto.response.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        int status,
        String message,
        Instant timestamp,
        Map<String, String> errors
) {
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