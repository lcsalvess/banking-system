package com.lucas.bankingsystem.event.account;

public record AccountOperationEvent(
        Long accountId,
        AccountOperationType type
) {
}
