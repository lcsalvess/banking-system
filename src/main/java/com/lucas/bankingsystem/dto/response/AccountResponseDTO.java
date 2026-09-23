package com.lucas.bankingsystem.dto.response;

import com.lucas.bankingsystem.entity.Account;
import com.lucas.bankingsystem.entity.enums.AccountStatus;
import com.lucas.bankingsystem.entity.enums.AccountType;

import java.math.BigDecimal;

public record AccountResponseDTO(
        String accountNumber,
        String accountDigit,
        String clientName,
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
