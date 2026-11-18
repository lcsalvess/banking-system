package com.lcsalvess.bankingsystem.event.user;

/**
 * Represents an event describing an operation performed on a user account.
 *
 * @param userId the identifier of the affected user
 * @param type the type of user operation performed
 */
public record UserOperationEvent(
    Long userId,
    UserOperationType type
) {
}
