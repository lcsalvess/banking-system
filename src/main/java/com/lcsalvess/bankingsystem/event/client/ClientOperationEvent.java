package com.lcsalvess.bankingsystem.event.client;

/**
 * Represents a client operation event published after a client-related transaction.
 *
 * @param clientId the identifier of the affected client
 * @param type the type of operation performed on the client
 * @param username the username of the user responsible for the operation
 */
public record ClientOperationEvent(
    Long clientId,
    ClientOperationType type,
    String username
) {
}
