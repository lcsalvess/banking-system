package com.lcsalvess.bankingsystem.dto.request;

import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.passwordbytelength.PasswordByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record UserRequestDTO(
        @Schema(
                description = "Unique username. Only letters, numbers, dots, underscores and hyphens are allowed.",
                example = "john.doe",
                maxLength = 20
        )
        @NotBlank(message = ValidationMessages.REQUIRED_USERNAME)
        @Size(max = 20, message = ValidationMessages.INVALID_USERNAME_LENGTH)
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = ValidationMessages.INVALID_USERNAME_FORMAT
        )
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
                description = "User's password. Must contain at least 8 characters and comply with the password byte-length limit.",
                example = "SecurePass123!"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_PASSWORD)
        @Size(min = 8, message = ValidationMessages.PASSWORD_TOO_SHORT)
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
