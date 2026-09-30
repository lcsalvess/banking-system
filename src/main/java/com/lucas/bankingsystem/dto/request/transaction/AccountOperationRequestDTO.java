package com.lucas.bankingsystem.dto.request.transaction;

import jakarta.validation.constraints.*;

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
        @Digits(
                integer = 17,
                fraction = 2,
                message = "O valor deve ter no máximo 17 dígitos inteiros e 2 casas decimais."
        )
        BigDecimal amount) {

}
