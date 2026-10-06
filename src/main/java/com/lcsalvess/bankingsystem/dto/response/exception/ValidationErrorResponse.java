package com.lcsalvess.bankingsystem.dto.response.exception;

import java.util.Map;

public record ValidationErrorResponse(
        int status,
        String message,
        Map<String, String> errors
) {
}
