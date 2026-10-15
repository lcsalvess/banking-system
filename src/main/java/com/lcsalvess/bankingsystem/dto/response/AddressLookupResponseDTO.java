package com.lcsalvess.bankingsystem.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AddressLookupResponseDTO(
        @Schema(
                description = "Street name.",
                example = "Rua A - 4"
        )
        String streetName,

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
}
