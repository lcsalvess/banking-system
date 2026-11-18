package com.lcsalvess.bankingsystem.dto.response.account;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.AccountType;
import com.lcsalvess.bankingsystem.serialization.MoneySerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import tools.jackson.databind.annotation.JsonSerialize;

public record AccountResponseDto(
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
  public static AccountResponseDto fromEntity(Account account) {
    return new AccountResponseDto(
        account.getAccountNumber(),
        account.getDigit(),
        account.getClient().getName(),
        account.getBalance(),
        account.getType(),
        account.getStatus()
    );
  }
}
