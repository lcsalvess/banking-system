package com.lcsalvess.bankingsystem.dto.request;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientUpdateRequestDTO(
        @Size(max = 125, message = ValidationMessages.INVALID_CLIENT_NAME_LENGTH)
        @Pattern(regexp = "(?s).*\\S.*", message = ValidationMessages.INVALID_CLIENT_NAME_BLANK)
        String name,
        @Email(message = ValidationMessages.INVALID_EMAIL)
        @Size(max = 254, message = ValidationMessages.INVALID_EMAIL_LENGTH)
        @Pattern(regexp = "(?s).*\\S.*", message = ValidationMessages.INVALID_EMAIL_BLANK)
        String email,
        @Pattern(regexp = "^[0-9]{10,11}$", message = ValidationMessages.INVALID_PHONE_NUMBER_FORMAT)
        String phoneNumber,
        @Valid
        AddressUpdateRequestDTO address
) {
}