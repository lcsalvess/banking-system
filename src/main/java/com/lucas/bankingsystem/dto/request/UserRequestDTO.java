package com.lucas.bankingsystem.dto.request;

import com.lucas.bankingsystem.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRequestDTO(
        @NotBlank(message = "O nome de usuário é obrigatório.")
        @Size(max = 20, message = "O nome de usuário deve ter no máximo 20 caracteres.")
        String username,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "O e-mail deve ser válido.")
        String email,

        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres.")
        String password,

        @NotNull(message = "O perfil do usuário é obrigatório.")
        Role role
) {
}
