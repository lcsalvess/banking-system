package com.lucas.bankingsystem.event.client;

public record ClientOperationEvent(
        Long clientId,
        ClientOperationType type
) {
}
