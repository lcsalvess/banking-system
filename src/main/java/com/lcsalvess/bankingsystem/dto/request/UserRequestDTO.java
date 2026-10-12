package com.lcsalvess.bankingsystem.dto.request;

import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.validation.passwordbytelength.PasswordByteLength;
import jakarta.validation.constraints.*;

public record UserRequestDTO(
        @NotBlank(message = "O nome de usuário é obrigatório.")
        @Size(max = 20, message = "O nome de usuário deve ter no máximo 20 caracteres.")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = "O nome de usuário pode conter apenas letras, números, ponto, hífen e underscore."
        )
        String username,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "O e-mail deve ser válido.")
        String email,

        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres.")
        @PasswordByteLength
        String password,

        @NotNull(message = "O perfil do usuário é obrigatório.")
        Role role
) {
}
