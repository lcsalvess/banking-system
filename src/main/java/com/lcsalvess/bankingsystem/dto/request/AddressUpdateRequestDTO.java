package com.lcsalvess.bankingsystem.dto.request;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressUpdateRequestDTO(

        @NotBlank(message = ValidationMessages.REQUIRED_STREET_NUMBER)
        @Size(max = 10, message = ValidationMessages.STREET_NUMBER_EXCEEDS_MAX_LENGTH)
        String streetNumber,

        @Size(max = 100, message = ValidationMessages.INVALID_COMPLEMENT_LENGTH)
        String complement,

        @NotBlank(message = ValidationMessages.REQUIRED_POSTAL_CODE)
        @Pattern(regexp = "^[0-9]{8}$", message = ValidationMessages.INVALID_POSTAL_CODE_FORMAT)
        String postalCode) {
}