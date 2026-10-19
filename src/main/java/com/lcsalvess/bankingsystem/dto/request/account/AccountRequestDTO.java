package com.lcsalvess.bankingsystem.dto.request.account;

import com.lcsalvess.bankingsystem.entity.account.AccountType;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AccountRequestDTO(
        @Schema(
                description = "Unique identifier of the client who will own the account.",
                example = "1"
        )
        @NotNull(message = ValidationMessages.REQUIRED_HOLDER_ID)
        @Positive(message = ValidationMessages.POSITIVE_HOLDER_ID)
        Long clientId,

        @Schema(
                description = "Type of bank account to create.",
                example = "CHECKING"
        )
        @NotNull(message = ValidationMessages.REQUIRED_ACCOUNT_TYPE)
        AccountType type
) {
}
