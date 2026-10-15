package com.lcsalvess.bankingsystem.dto.request;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.cpf.ValidCpf;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ClientRequestDTO(
        @Schema(
                description = "Full name of the client.",
                example = "Pietro Kauê"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_CLIENT_NAME)
        @Size(max = 125, message = ValidationMessages.INVALID_CLIENT_NAME_LENGTH)
        String name,

        @Schema(
                description = "Client's CPF containing exactly 11 digits, without punctuation.",
                example = "35753494951"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_CPF)
        @Pattern(regexp = "^[0-9]{11}$", message = ValidationMessages.INVALID_CPF_LENGTH)
        @ValidCpf
        String cpf,

        @Schema(
                description = "Client's email address.",
                example = "pietro.kaue@example.com"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_EMAIL)
        @Email(message = ValidationMessages.INVALID_EMAIL)
        @Size(max = 254, message = ValidationMessages.INVALID_EMAIL_LENGTH)
        String email,

        @Schema(
                description = "Phone number containing the area code and number, digits only.",
                example = "11987654321"
        )
        @NotBlank(message = ValidationMessages.REQUIRED_PHONE_NUMBER)
        @Pattern(regexp = "^[0-9]{10,11}$", message = ValidationMessages.INVALID_PHONE_NUMBER_FORMAT)
        String phoneNumber,

        @Schema(
                description = "Client's address information."
        )
        @NotNull(message = ValidationMessages.REQUIRED_ADDRESS)
        @Valid
        AddressRequestDTO address
) {
}
