package com.lucas.bankingsystem.dto.response.security;

import com.lucas.bankingsystem.entity.enums.Role;

public record LoginResponseDTO(
        String token,
        String username,
        String email,
        Role role
) {
}
