package com.lucas.sistemabancario.dto.request.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequestDTO(
        @NotNull(message = "O número da conta de origem é obrigatório.")
        String fromAccountNumber,

        @NotNull(message = "O número da conta de destino é obrigatório.")
        String toAccountNumber,

        @NotNull(message = "O valor da transferência é obrigatório.")
        @Positive(message = "O valor da transferência deve ser maior que zero.")
        BigDecimal amount) {
}
