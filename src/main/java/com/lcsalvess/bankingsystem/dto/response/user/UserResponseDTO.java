package com.lcsalvess.bankingsystem.dto.response.user;

import com.lcsalvess.bankingsystem.entity.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record UserResponseDTO(

        @Schema(
                description = "Unique employee identifier assigned to the user.",
                example = "EMP-000004",
                maxLength = 30
        )
        String employeeId,

        @Schema(
                description = "User's full name. May be null for legacy accounts "
                        + "whose names have not yet been populated.",
                example = "John Michael Doe",
                maxLength = 150
        )
        String fullName,

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
                example = "2026-10-16T17:30:00Z"
        )
        Instant createdAt,

        @Schema(
                description = "Date and time when the user was last updated.",
                example = "2026-10-16T17:31:00Z"
        )
        Instant updatedAt
) {
}
