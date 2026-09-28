package com.lucas.bankingsystem.dto.response.exception;

import java.util.Map;

public record ValidationErrorResponse(
        int status,
        String message,
        Map<String, String> errors
) {
}
