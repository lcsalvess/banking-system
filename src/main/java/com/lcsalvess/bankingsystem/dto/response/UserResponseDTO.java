package com.lcsalvess.bankingsystem.dto.response;

import com.lcsalvess.bankingsystem.entity.enums.Role;

public record UserResponseDTO(
        String username,
        String email,
        Role role,
        boolean active
) {
}
