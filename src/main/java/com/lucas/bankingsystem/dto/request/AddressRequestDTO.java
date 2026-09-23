package com.lucas.bankingsystem.dto.request;

import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.entity.enums.StreetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequestDTO(
        @NotNull(message = "O tipo do logradouro é obrigatório.")
        StreetType streetType,

        @NotBlank(message = "O logradouro não pode ser vazio.")
        @Size(max = 150, message = "O logradouro deve ter no máximo 150 caracteres.")
        String streetName,

        @NotBlank(message = "O número não pode ser vazio.")
        @Size(max = 10, message = "O número deve ter no máximo 10 caracteres.")
        String streetNumber,

        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres.")
        String complement,

        @NotBlank(message = "O bairro não pode ser vazio.")
        @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres.")
        String neighborhood,

        @NotBlank(message = "A cidade não pode ser vazia.")
        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres.")
        String city,

        @NotNull(message = "O estado é obrigatório.")
        State state,

        @NotBlank(message = "O CEP é obrigatório.")
        @Pattern(regexp = "^[0-9]{8}$", message = "O CEP deve conter exatamente 8 números, sem traços ou espaços.")
        String postalCode) {
}
