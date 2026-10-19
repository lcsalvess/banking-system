package com.lcsalvess.bankingsystem.integration.address.dto;

import com.lcsalvess.bankingsystem.integration.address.validation.ValidState;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddressLookupResponse(
        @NotBlank(message = ValidationMessages.REQUIRED_STREET_NAME)
        String streetName,

        @NotBlank(message = ValidationMessages.REQUIRED_NEIGHBORHOOD)
        String neighborhood,

        @NotBlank(message = ValidationMessages.REQUIRED_CITY)
        String city,

        @NotBlank(message = ValidationMessages.REQUIRED_STATE)
        @ValidState
        String state,

        @NotBlank(message = ValidationMessages.REQUIRED_POSTAL_CODE)
        @Pattern(
                regexp = "^\\d{8}$",
                message = ValidationMessages.INVALID_POSTAL_CODE_FORMAT
        )
        String postalCode
) {
}