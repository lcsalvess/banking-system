package com.lcsalvess.bankingsystem.dto.request.security;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.passwordbytelength.PasswordByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequestDTO(
        @Schema(
                description = "Current password of the authenticated user.",
                example = "SecurePass123!"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_CURRENT_PASSWORD)
        @Size(min = 8, max = 64, message = ValidationMessages.PASSWORD_TOO_SHORT_OR_LONG)
        @PasswordByteLength
        String currentPassword,

        @Schema(
                description = "New password. Must contain between 8 and 64 characters, must not exceed 72 bytes when encoded in UTF-8 and must differ from the current password.",
                example = "EvenMoreSecure456!"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_NEW_PASSWORD)
        @Size(min = 8, max = 64, message = ValidationMessages.PASSWORD_TOO_SHORT_OR_LONG)
        @PasswordByteLength
        String newPassword
) {
}
