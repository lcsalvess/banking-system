package com.lucas.sistemabancario.dto.request;

import com.lucas.sistemabancario.entity.enums.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AccountRequestDTO(
        @NotNull(message = "O ID do titular é obrigatório.")
        @Positive(message = "O ID do titular deve ser maior que zero.")
        Long clientId,

        @NotNull(message = "O tipo de conta é obrigatório.")
        AccountType type
) {
}
