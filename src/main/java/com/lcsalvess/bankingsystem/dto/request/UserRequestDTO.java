package com.lcsalvess.bankingsystem.dto.request;

import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.passwordbytelength.PasswordByteLength;
import com.lcsalvess.bankingsystem.validation.reservedusername.NotReservedUsername;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record UserRequestDTO(
        @Schema(
                description = "Unique username. Only letters, numbers, dots, underscores and hyphens are allowed. The name \"system\" is reserved.",
                example = "john.doe",
                maxLength = 20
        )
        @NotBlank(message = ValidationMessages.REQUIRED_USERNAME)
        @Size(max = 20, message = ValidationMessages.INVALID_USERNAME_LENGTH)
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = ValidationMessages.INVALID_USERNAME_FORMAT
        )
        @NotReservedUsername
        String username,

        @Schema(
                description = "User's email address.",
                example = "john.doe@example.com",
                maxLength = 254
        )
        @NotBlank(message = ValidationMessages.REQUIRED_EMAIL)
        @Size(max = 254, message = ValidationMessages.INVALID_EMAIL_LENGTH)
        @Email(message = ValidationMessages.INVALID_EMAIL)
        String email,

        @Schema(
                description = "User's password. Must contain between 8 and 64 characters and must not exceed 72 bytes when encoded in UTF-8.",
                example = "SecurePass123!"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_PASSWORD)
        @Size(min = 8, max = 64, message = ValidationMessages.PASSWORD_TOO_SHORT_OR_LONG)
        @PasswordByteLength
        String password,

        @Schema(
                description = "Role assigned to the user.",
                example = "EMPLOYEE"
        )
        @NotNull(message = ValidationMessages.REQUIRED_USER_ROLE)
        Role role
) {
}