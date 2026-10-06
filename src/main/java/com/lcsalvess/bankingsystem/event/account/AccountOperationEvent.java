package com.lcsalvess.bankingsystem.event.account;

public record AccountOperationEvent(
        Long accountId,
        AccountOperationType type,
        String username
) {
}
