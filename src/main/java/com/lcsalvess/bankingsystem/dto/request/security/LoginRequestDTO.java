package com.lcsalvess.bankingsystem.dto.request.security;

import com.lcsalvess.bankingsystem.validation.passwordbytelength.PasswordByteLength;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "O nome de usuário é obrigatório.")
        String username,

        @NotBlank(message = "A senha é obrigatória.")
        @PasswordByteLength
        String password
) {
}
