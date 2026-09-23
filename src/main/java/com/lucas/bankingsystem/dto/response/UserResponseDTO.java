package com.lucas.bankingsystem.dto.response;

import com.lucas.bankingsystem.entity.enums.Role;

public record UserResponseDTO(
        String username,
        String email,
        Role role,
        boolean active
) {
}
