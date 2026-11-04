package com.lcsalvess.bankingsystem.dto.request.user;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequestDTO(
        @Schema(
                description = "New role for the user.",
                example = "EMPLOYEE"
        )
        @NotNull(message = ValidationMessages.REQUIRED_USER_ROLE)
        Role role
) {
}
