package com.lucas.bankingsystem.event.user;

public record UserOperationEvent(
        Long userId,
        UserOperationType type
) {
}
