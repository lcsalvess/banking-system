package com.lcsalvess.bankingsystem.event.client;

public record ClientOperationEvent(
        Long clientId,
        ClientOperationType type,
        String username
) {
}
