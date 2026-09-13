package com.lucas.sistemabancario.dto.request.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AccountOperationRequestDTO(
        @NotNull(message = "O ID da conta é obrigatório.")
        Long accountId,

        @NotNull(message = "O amount da operação é obrigatório.")
        @Positive(message = "O amount da operação deve ser maior que zero.")
        BigDecimal amount) {

}
