package com.lucas.bankingsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequestDTO(

        @NotBlank(message = "O número não pode ser vazio.")
        @Size(max = 10, message = "O número deve ter no máximo 10 caracteres.")
        String streetNumber,

        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres.")
        String complement,

        @NotBlank(message = "O CEP é obrigatório.")
        @Pattern(
                regexp = "^[0-9]{8}$",
                message = "O CEP deve conter exatamente 8 números, sem traços ou espaços."
        )
        String postalCode
) {
}