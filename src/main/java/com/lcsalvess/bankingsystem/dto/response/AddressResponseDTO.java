package com.lcsalvess.bankingsystem.dto.response;

import com.lcsalvess.bankingsystem.entity.Address;
import io.swagger.v3.oas.annotations.media.Schema;

public record AddressResponseDTO(
        @Schema(
                description = "Street name.",
                example = "Rua A - 4"
        )
        String streetName,

        @Schema(
                description = "Street or building number.",
                example = "123"
        )
        String streetNumber,

        @Schema(
                description = "Additional address information, if applicable.",
                example = "Apartamento 42"
        )
        String complement,

        @Schema(
                description = "Neighborhood name.",
                example = "Poço"
        )
        String neighborhood,

        @Schema(
                description = "City name.",
                example = "Maceió"
        )
        String city,

        @Schema(
                description = "Brazilian state abbreviation.",
                example = "AL"
        )
        String state,

        @Schema(
                description = "Brazilian postal code (CEP), containing exactly 8 digits without punctuation.",
                example = "57025672"
        )
        String postalCode
) {
    public static AddressResponseDTO fromEntity(Address address) {
        return new AddressResponseDTO(
                address.getStreetName(),
                address.getStreetNumber(),
                address.getComplement(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState().name(),
                address.getPostalCode()
        );
    }
}
