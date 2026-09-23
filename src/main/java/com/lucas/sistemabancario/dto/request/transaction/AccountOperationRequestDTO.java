package com.lucas.sistemabancario.dto.request.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AccountOperationRequestDTO(
        @NotBlank(message = "O número da conta é obrigatório.")
        @Pattern(
                regexp = "^\\d{5}$",
                message = "O número da conta deve conter exatamente 5 dígitos."
        )
        String accountNumber,

        @NotBlank(message = "O dígito da conta é obrigatório.")
        @Pattern(regexp = "^\\d$", message = "O dígito da conta deve conter exatamente 1 dígito.")
        String digit,

        @NotNull(message = "O valor da operação é obrigatório.")
        @Positive(message = "O valor da operação deve ser maior que zero.")
        BigDecimal amount) {

}
