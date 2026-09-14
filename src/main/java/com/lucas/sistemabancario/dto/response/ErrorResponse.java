package com.lucas.sistemabancario.dto.response;

public record ErrorResponse(
        int status,
        String message
) {
}
