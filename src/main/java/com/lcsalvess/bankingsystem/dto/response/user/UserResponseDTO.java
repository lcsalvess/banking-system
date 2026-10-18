package com.lcsalvess.bankingsystem.dto.response.user;

import com.lcsalvess.bankingsystem.entity.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record UserResponseDTO(
        @Schema(
                description = "Username assigned to the user.",
                example = "john.doe"
        )
        String username,

        @Schema(
                description = "User's email address.",
                example = "john.doe@example.com"
        )
        String email,

        @Schema(
                description = "Role assigned to the user.",
                example = "EMPLOYEE"
        )
        Role role,

        @Schema(
                description = "Indicates whether the user account is active.",
                example = "true"
        )
        boolean active,

        @Schema(
                description = "Date and time when the user was created.",
                example = "2026-10-16T14:30:00"
        )
        LocalDateTime createdAt,

        @Schema(
                description = "Date and time when the user was last updated.",
                example = "2026-10-16T15:45:00"
        )
        LocalDateTime updatedAt
) {
}
