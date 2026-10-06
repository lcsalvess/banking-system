package com.lcsalvess.bankingsystem.dto.response.security;

import com.lcsalvess.bankingsystem.entity.enums.Role;

public record LoginResponseDTO(
        String token,
        String username,
        String email,
        Role role
) {
}
