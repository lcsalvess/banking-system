package com.lucas.sistemabancario.dto.request.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequestDTO(
        @NotNull(message = "O ID da conta de origem é obrigatório.")
        Long fromAccountId,

        @NotNull(message = "O ID da conta de destino é obrigatório.")
        Long toAccountId,

        @NotNull(message = "O amount da transferência é obrigatório.")
        @Positive(message = "O amount da transferência deve ser maior que zero.")
        BigDecimal amount) {
}
