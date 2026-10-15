package com.lcsalvess.bankingsystem.dto.request.transaction;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountDigit;
import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequestDTO(
        @Schema(
                description = "Source account number.",
                example = "00001"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_SOURCE_ACCOUNT_NUMBER)
        @ValidAccountNumber
        String fromAccountNumber,

        @Schema(
                description = "Check digit of the source account.",
                example = "9"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_SOURCE_ACCOUNT_DIGIT)
        @ValidAccountDigit
        String fromAccountDigit,

        @Schema(
                description = "Destination account number.",
                example = "00004"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_DESTINATION_ACCOUNT_NUMBER)
        @ValidAccountNumber
        String toAccountNumber,

        @Schema(
                description = "Check digit of the destination account.",
                example = "3"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_DESTINATION_ACCOUNT_DIGIT)
        @ValidAccountDigit
        String toAccountDigit,

        @Schema(
                description = "Positive amount to transfer, with up to two decimal places.",
                example = "150.50"
        )
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
