package com.lcsalvess.bankingsystem.event.user;

import com.lcsalvess.bankingsystem.entity.user.Role;

public record UserRoleChangedEvent(
        Long userId,
        Role previousRole,
        Role newRole
) {
}
