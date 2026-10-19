package com.lcsalvess.bankingsystem.dto.request.address;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequestDTO(

        @Schema(
                description = "Street or building number.",
                example = "123"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_STREET_NUMBER)
        @Size(max = 10, message = ValidationMessages.STREET_NUMBER_EXCEEDS_MAX_LENGTH)
        String streetNumber,

        @Schema(
                description = "Additional address information, if applicable.",
                example = "Apartamento 42"
        )
        @Size(max = 100, message = ValidationMessages.INVALID_COMPLEMENT_LENGTH)
        String complement,

        @Schema(
                description = "Brazilian postal code (CEP), containing exactly 8 digits without punctuation.",
                example = "57025672"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_POSTAL_CODE)
        @Pattern(
                regexp = "^[0-9]{8}$",
                message = ValidationMessages.INVALID_POSTAL_CODE_FORMAT
        )
        String postalCode
) {
}