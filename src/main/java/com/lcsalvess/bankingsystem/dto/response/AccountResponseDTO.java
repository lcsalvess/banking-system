package com.lcsalvess.bankingsystem.dto.response;

import com.lcsalvess.bankingsystem.serialization.MoneySerializer;
import com.lcsalvess.bankingsystem.entity.Account;
import com.lcsalvess.bankingsystem.entity.enums.AccountStatus;
import com.lcsalvess.bankingsystem.entity.enums.AccountType;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;

public record AccountResponseDTO(
        String accountNumber,
        String accountDigit,
        String clientName,
        @JsonSerialize(using = MoneySerializer.class)
        BigDecimal balance,
        AccountType type,
        AccountStatus status) {
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
