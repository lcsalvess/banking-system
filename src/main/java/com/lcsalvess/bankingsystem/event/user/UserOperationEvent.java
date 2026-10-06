package com.lcsalvess.bankingsystem.event.user;

public record UserOperationEvent(
        Long userId,
        UserOperationType type
) {
}
