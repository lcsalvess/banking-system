package com.lucas.bankingsystem.dto.request;

import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.entity.enums.StreetType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressUpdateRequestDTO (
        StreetType streetType,

        @Size(max = 150, message = "O logradouro deve ter no máximo 150 caracteres.")
        String streetName,

        @Size(max = 10, message = "O número deve ter no máximo 10 caracteres.")
        String streetNumber,

        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres.")
        String complement,

        @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres.")
        String neighborhood,

        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres.")
        String city,

        State state,

        @Pattern(regexp = "^[0-9]{8}$", message = "O CEP deve conter exatamente 8 números, sem traços ou espaços.")
        String postalCode
) {
}
