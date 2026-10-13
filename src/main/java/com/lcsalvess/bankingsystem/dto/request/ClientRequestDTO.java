package com.lcsalvess.bankingsystem.dto.request;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.cpf.ValidCpf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ClientRequestDTO(
        @NotBlank(message = ValidationMessages.REQUIRED_CLIENT_NAME)
        @Size(max = 125, message = ValidationMessages.INVALID_CLIENT_NAME_LENGTH)
        String name,
        @NotBlank(message = ValidationMessages.REQUIRED_CPF)
        @Pattern(regexp = "^[0-9]{11}$", message = ValidationMessages.INVALID_CPF_LENGTH)
        @ValidCpf
        String cpf,
        @NotBlank(message = ValidationMessages.REQUIRED_EMAIL)
        @Email(message = ValidationMessages.INVALID_EMAIL)
        @Size(max = 254, message = ValidationMessages.INVALID_EMAIL_LENGTH)
        String email,
        @NotBlank(message = ValidationMessages.REQUIRED_PHONE_NUMBER)
        @Pattern(regexp = "^[0-9]{10,11}$", message = ValidationMessages.INVALID_PHONE_NUMBER_FORMAT)
        String phoneNumber,
        @NotNull(message = ValidationMessages.REQUIRED_ADDRESS)
        @Valid
        AddressRequestDTO address
) {
}
