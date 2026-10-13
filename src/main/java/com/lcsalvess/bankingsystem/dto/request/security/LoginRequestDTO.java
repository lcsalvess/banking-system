package com.lcsalvess.bankingsystem.dto.request.security;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.passwordbytelength.PasswordByteLength;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = ValidationMessages.REQUIRED_USERNAME)
        String username,

        @NotBlank(message = ValidationMessages.REQUIRED_PASSWORD)
        @PasswordByteLength
        String password
) {
}
