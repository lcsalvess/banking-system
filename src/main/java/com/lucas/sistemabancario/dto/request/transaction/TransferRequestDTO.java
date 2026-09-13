package com.lucas.sistemabancario.dto.request.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequestDTO(
        @NotBlank(message = "O número da conta de origem é obrigatório.")
        @Pattern(
                regexp = "^\\d{6}$",
                message = "O número da conta de origem deve conter exatamente 6 dígitos."
        )
        String fromAccountNumber,

        @NotBlank(message = "O número da conta de destino é obrigatório.")
        @Pattern(
                regexp = "^\\d{6}$",
                message = "O número da conta de destino deve conter exatamente 6 dígitos."
        )
        String toAccountNumber,

        @NotNull(message = "O valor da transferência é obrigatório.")
        @Positive(message = "O valor da transferência deve ser maior que zero.")
        BigDecimal amount) {
}
