package com.lucas.sistemabancario.dto.request.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AccountOperationRequestDTO(
        @NotNull(message = "O número da conta é obrigatório.")
        String accountNumber,

        @NotNull(message = "O valor da operação é obrigatório.")
        @Positive(message = "O valor da operação deve ser maior que zero.")
        BigDecimal amount) {

}
