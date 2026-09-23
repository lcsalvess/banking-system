package com.lucas.bankingsystem.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ClientRequestDTO(
        @NotBlank(message = "O nome é obrigatório.") @Size(max = 125, message = "O nome deve ter no máximo 125 caracteres.") String name,
        @NotBlank(message = "O CPF é obrigatório.") @Pattern(regexp = "^[0-9]{11}$", message = "O CPF deve conter exatamente 11 números.") String cpf,
        @NotBlank(message = "O e-mail é obrigatório.") @Email(message = "O formato do e-mail é inválido.") @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres.") String email,
        @NotBlank(message = "O telefone é obrigatório.") @Pattern(regexp = "^[0-9]{10,11}$", message = "O telefone deve conter de 10 a 11 números, incluindo o DDD.") String phoneNumber,
        @NotNull(message = "Os dados de endereço são obrigatórios.") @Valid AddressRequestDTO address
) {
}
