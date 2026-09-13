package com.lucas.sistemabancario.dto.request;

import com.lucas.sistemabancario.entity.enums.AccountType;
import jakarta.validation.constraints.NotNull;

public record AccountRequestDTO(
        @NotNull(message = "O ID do titular é obrigatório.")
        Long clientId,

        @NotNull(message = "O tipo de conta é obrigatório.")
        AccountType accountType
) {
}
