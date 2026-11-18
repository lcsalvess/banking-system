package com.lcsalvess.bankingsystem.event.user;

import com.lcsalvess.bankingsystem.entity.user.Role;

/**
 * Represents an event describing a user's role change.
 *
 * @param userId the identifier of the affected user
 * @param previousRole the role assigned before the change
 * @param newRole the role assigned after the change
 */
public record UserRoleChangedEvent(
    Long userId,
    Role previousRole,
    Role newRole
) {
}
