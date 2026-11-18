package com.lcsalvess.bankingsystem.dto.request.transaction;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountDigit;
import com.lcsalvess.bankingsystem.validation.accountnumber.ValidAccountNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AccountOperationRequestDto(
    @Schema(description = "Account number.", example = "00001")
    @NotBlank(message = ValidationMessages.REQUIRED_ACCOUNT_NUMBER)
    @ValidAccountNumber
    String accountNumber,

    @Schema(description = "Account check digit.", example = "9")
    @NotBlank(message = ValidationMessages.REQUIRED_ACCOUNT_DIGIT)
    @ValidAccountDigit
    String digit,

    @Schema(
        description = "Positive amount for the account operation, with up to two decimal places.",
        example = "150.50"
    )
    @NotNull(message = ValidationMessages.REQUIRED_OPERATION_AMOUNT)
    @Positive(message = ValidationMessages.POSITIVE_OPERATION_AMOUNT)
    @Digits(integer = 13, fraction = 2, message = ValidationMessages.INVALID_AMOUNT_PRECISION)
    BigDecimal amount
) {
}
