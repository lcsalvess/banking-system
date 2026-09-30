package com.lucas.bankingsystem.integration.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddressLookupResponse(
        @NotBlank(message = "O logradouro é obrigatório.")
        String streetName,
        @NotBlank(message = "O bairro é obrigatório.")
        String neighborhood,
        @NotBlank(message = "A cidade é obrigatória.")
        String city,
        @NotBlank(message = "O estado é obrigatório.")
        String state,
        @NotBlank(message = "O CEP é obrigatório.") @Pattern( regexp = "^\\d{8}$", message = "O CEP deve conter exatamente 8 dígitos." )
        String postalCode
) {
}
