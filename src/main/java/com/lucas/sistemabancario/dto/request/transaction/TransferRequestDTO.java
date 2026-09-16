package com.lucas.sistemabancario.dto.request.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequestDTO(
        @NotBlank(message = "O número da conta de origem é obrigatório.")
        @Pattern(
                regexp = "^\\d{5}$",
                message = "O número da conta de origem deve conter exatamente 5 dígitos."
        )
        String fromAccountNumber,

        @NotBlank(message = "O dígito da conta de origem é obrigatório.")
        @Pattern(regexp = "^\\d{1}$", message = "O dígito da conta de origem deve conter exatamente 1 dígito.")
        String fromAccountDigit,

        @NotBlank(message = "O número da conta de destino é obrigatório.")
        @Pattern(
                regexp = "^\\d{5}$",
                message = "O número da conta de destino deve conter exatamente 5 dígitos."
        )
        String toAccountNumber,

        @NotBlank(message = "O dígito da conta de destino é obrigatório.")
        @Pattern(regexp = "^\\d{1}$", message = "O dígito da conta de destino deve conter exatamente 1 dígito.")
        String toAccountDigit,

        @NotNull(message = "O valor da transferência é obrigatório.")
        @Positive(message = "O valor da transferência deve ser maior que zero.")
        BigDecimal amount) {
}
