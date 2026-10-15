package com.lcsalvess.bankingsystem.dto.request;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientUpdateRequestDTO(
        @Schema(
                description = "New full name of the client.",
                example = "Pietro Kauê"
        )
        @Size(max = 125, message = ValidationMessages.INVALID_CLIENT_NAME_LENGTH)
        @Pattern(regexp = "(?s).*\\S.*", message = ValidationMessages.INVALID_CLIENT_NAME_BLANK)
        String name,

        @Schema(
                description = "New email address of the client.",
                example = "kaue.pietro@example.com"
        )
        @Email(message = ValidationMessages.INVALID_EMAIL)
        @Size(max = 254, message = ValidationMessages.INVALID_EMAIL_LENGTH)
        @Pattern(regexp = "(?s).*\\S.*", message = ValidationMessages.INVALID_EMAIL_BLANK)
        String email,

        @Schema(
                description = "New phone number, including area code and digits only.",
                example = "11987654321"
        )
        @Pattern(regexp = "^[0-9]{10,11}$", message = ValidationMessages.INVALID_PHONE_NUMBER_FORMAT)
        String phoneNumber,

        @Schema(
                description = "Address fields to update."
        )
        @Valid
        AddressUpdateRequestDTO address
) {
}