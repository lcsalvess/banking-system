package com.lcsalvess.bankingsystem.dto.response;

import com.lcsalvess.bankingsystem.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserSummaryResponseDTO(
        @Schema(
                description = "Unique identifier of the user.",
                example = "4"
        )
        Long id,

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
