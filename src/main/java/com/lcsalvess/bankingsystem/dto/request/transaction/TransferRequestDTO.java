package com.lcsalvess.bankingsystem.dto.request.transaction;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountDigit;
import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountNumber;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequestDTO(
        @NotBlank(message = ValidationMessages.REQUIRED_SOURCE_ACCOUNT_NUMBER)
        @ValidAccountNumber
        String fromAccountNumber,

        @NotBlank(message = ValidationMessages.REQUIRED_SOURCE_ACCOUNT_DIGIT)
        @ValidAccountDigit
        String fromAccountDigit,

        @NotBlank(message = ValidationMessages.REQUIRED_DESTINATION_ACCOUNT_NUMBER)
        @ValidAccountNumber
        String toAccountNumber,

        @NotBlank(message = ValidationMessages.REQUIRED_DESTINATION_ACCOUNT_DIGIT)
        @ValidAccountDigit
        String toAccountDigit,

        @NotNull(message = ValidationMessages.REQUIRED_TRANSFER_AMOUNT)
        @Positive(message = ValidationMessages.POSITIVE_TRANSFER_AMOUNT)
        @Digits(
                integer = 17,
                fraction = 2,
                message = ValidationMessages.INVALID_AMOUNT_PRECISION
        )
        BigDecimal amount
) {
}
