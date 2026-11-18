package com.lcsalvess.bankingsystem.event.account;

/**
 * Represents an account lifecycle operation performed by a user.
 *
 * @param accountId identifier of the affected account
 * @param type type of account operation performed
 * @param username username of the actor who performed the operation
 */
public record AccountOperationEvent(
    Long accountId,
    AccountOperationType type,
    String username
) {
}
