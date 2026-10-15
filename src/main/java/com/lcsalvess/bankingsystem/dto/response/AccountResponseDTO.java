package com.lcsalvess.bankingsystem.dto.response;

import com.lcsalvess.bankingsystem.serialization.MoneySerializer;
import com.lcsalvess.bankingsystem.entity.Account;
import com.lcsalvess.bankingsystem.entity.enums.AccountStatus;
import com.lcsalvess.bankingsystem.entity.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;

public record AccountResponseDTO(
        @Schema(
                description = "Account number.",
                example = "00001"
        )
        String accountNumber,

        @Schema(
                description = "Account check digit.",
                example = "9"
        )
        String accountDigit,

        @Schema(
                description = "Full name of the account holder.",
                example = "Pietro Kauê"
        )
        String clientName,

        @Schema(
                description = "Current account balance, formatted as monetary value.",
                example = "1500.50"
        )
        @JsonSerialize(using = MoneySerializer.class)
        BigDecimal balance,

        @Schema(
                description = "Type of bank account.",
                example = "CHECKING"
        )
        AccountType type,

        @Schema(
                description = "Current account status.",
                example = "ACTIVE"
        )
        AccountStatus status
) {
    public static AccountResponseDTO fromEntity(Account account) {
        return new AccountResponseDTO(
                account.getAccountNumber(),
                account.getDigit(),
                account.getClient().getName(),
                account.getBalance(),
                account.getType(),
                account.getStatus()
        );
    }
}
