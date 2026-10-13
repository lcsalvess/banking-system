package com.lcsalvess.bankingsystem.dto.request.transaction;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountDigit;
import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountNumber;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record AccountOperationRequestDTO(
        @NotBlank(message = ValidationMessages.REQUIRED_ACCOUNT_NUMBER)
        @ValidAccountNumber
        String accountNumber,

        @NotBlank(message = ValidationMessages.REQUIRED_ACCOUNT_DIGIT)
        @ValidAccountDigit
        String digit,

        @NotNull(message = ValidationMessages.REQUIRED_OPERATION_AMOUNT)
        @Positive(message = ValidationMessages.POSITIVE_OPERATION_AMOUNT)
        @Digits(
                integer = 17,
                fraction = 2,
                message = ValidationMessages.INVALID_AMOUNT_PRECISION
        )
        BigDecimal amount
) {
}
