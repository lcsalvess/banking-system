package com.lucas.sistemabancario.dto.response;

import com.lucas.sistemabancario.entity.enums.Role;

public record UserResponseDTO(
        String username,
        String email,
        Role role,
        boolean active
) {
}
