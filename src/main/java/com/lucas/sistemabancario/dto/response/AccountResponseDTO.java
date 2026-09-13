package com.lucas.sistemabancario.dto.response;

import com.lucas.sistemabancario.entity.Account;
import com.lucas.sistemabancario.entity.enums.AccountStatus;
import com.lucas.sistemabancario.entity.enums.AccountType;

import java.math.BigDecimal;

public record AccountResponseDTO(
        Long id,
        String accountNumber,
        String clientName,
        BigDecimal balance,
        AccountType accountType,
        AccountStatus accountStatus) {
    public static AccountResponseDTO fromEntity(Account account) {
        return new AccountResponseDTO(
                account.getId(),
                account.getAccountNumber(),
                account.getClient().getName(),
                account.getBalance(),
                account.getType(),
                account.getStatus()
        );
    }
}
