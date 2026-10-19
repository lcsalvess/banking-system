package com.lcsalvess.bankingsystem.dto.response.security;

import com.lcsalvess.bankingsystem.entity.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponseDTO(
        @Schema(
                description = "JWT access token used to authenticate requests.",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.signature"
        )
        String token,

        @Schema(
                description = "Username of the authenticated user.",
                example = "admin"
        )
        String username,

        @Schema(
                description = "Email address of the authenticated user.",
                example = "admin@example.com"
        )
        String email,

        @Schema(
                description = "Role assigned to the authenticated user.",
                example = "ADMIN"
        )
        Role role
) {
}
