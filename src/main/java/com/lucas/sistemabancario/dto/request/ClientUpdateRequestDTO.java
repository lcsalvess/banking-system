package com.lucas.sistemabancario.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientUpdateRequestDTO(
        @Size(max = 125, message = "O nome deve ter no máximo 125 caracteres.")
        String name,
        @Email(message = "O formato do e-mail é inválido.")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres.")
        String email,
        @Pattern(regexp = "^[0-9]{10,11}$", message = "O telefone deve conter de 10 a 11 números, incluindo o DDD.")
        String phoneNumber,
        @Valid
        AddressUpdateRequestDTO address
) {
}
