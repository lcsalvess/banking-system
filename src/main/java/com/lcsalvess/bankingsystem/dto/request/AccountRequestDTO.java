package com.lcsalvess.bankingsystem.dto.request;

import com.lcsalvess.bankingsystem.entity.enums.AccountType;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AccountRequestDTO(
        @NotNull(message = ValidationMessages.REQUIRED_HOLDER_ID)
        @Positive(message = ValidationMessages.POSITIVE_HOLDER_ID)
        Long clientId,

        @NotNull(message = ValidationMessages.REQUIRED_ACCOUNT_TYPE)
        AccountType type
) {
}
