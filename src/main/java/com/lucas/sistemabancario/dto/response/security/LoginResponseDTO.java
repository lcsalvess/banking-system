package com.lucas.sistemabancario.dto.response.security;

import com.lucas.sistemabancario.entity.enums.Role;

public record LoginResponseDTO(
        String username,
        String email,
        Role role
) {
}
