package com.lucas.sistemabancario.dto.response;

import com.lucas.sistemabancario.entity.Account;
import com.lucas.sistemabancario.entity.enums.AccountStatus;
import com.lucas.sistemabancario.entity.enums.AccountType;

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
