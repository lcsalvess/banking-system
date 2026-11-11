package com.lcsalvess.bankingsystem.dto.response.user;

import com.lcsalvess.bankingsystem.entity.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserSummaryResponseDTO(

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
        boolean active
) {
}
