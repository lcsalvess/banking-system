package com.lucas.bankingsystem.dto.request.transaction;

import com.lucas.bankingsystem.validation.accountnumber.ValidAccountDigit;
import com.lucas.bankingsystem.validation.accountnumber.ValidAccountNumber;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequestDTO(
        @NotBlank(message = "O número da conta de origem é obrigatório.")
        @ValidAccountNumber(message = "O número da conta de origem deve conter exatamente 5 dígitos.")
        String fromAccountNumber,

        @NotBlank(message = "O dígito da conta de origem é obrigatório.")
        @ValidAccountDigit(message = "O dígito da conta de origem deve conter exatamente 1 dígito.")
        String fromAccountDigit,

        @NotBlank(message = "O número da conta de destino é obrigatório.")
        @ValidAccountNumber(message = "O número da conta de destino deve conter exatamente 5 dígitos.")
        String toAccountNumber,

        @NotBlank(message = "O dígito da conta de destino é obrigatório.")
        @ValidAccountDigit(message = "O dígito da conta de destino deve conter exatamente 1 dígito.")
        String toAccountDigit,

        @NotNull(message = "O valor da transferência é obrigatório.")
        @Positive(message = "O valor da transferência deve ser maior que zero.")
        @Digits(
                integer = 17,
                fraction = 2,
                message = "O valor deve ter no máximo 17 dígitos inteiros e 2 casas decimais."
        )
        BigDecimal amount
) {
}
