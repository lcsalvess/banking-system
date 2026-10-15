package com.lcsalvess.bankingsystem.dto.request.security;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.passwordbytelength.PasswordByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @Schema(
                description = "Username registered in the system.",
                example = "admin"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_USERNAME)
        String username,

        @Schema(
                description = "Password associated with the username.",
                example = "Admin123!"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_PASSWORD)
        @PasswordByteLength
        String password
) {
}
