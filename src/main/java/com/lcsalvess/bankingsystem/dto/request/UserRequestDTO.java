package com.lcsalvess.bankingsystem.dto.request;

import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.passwordbytelength.PasswordByteLength;
import jakarta.validation.constraints.*;

public record UserRequestDTO(
        @NotBlank(message = ValidationMessages.REQUIRED_USERNAME)
        @Size(max = 20, message = ValidationMessages.INVALID_USERNAME_LENGTH)
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = ValidationMessages.INVALID_USERNAME_FORMAT
        )
        String username,

        @NotBlank(message = ValidationMessages.REQUIRED_EMAIL)
        @Size(max = 254, message = ValidationMessages.INVALID_EMAIL_LENGTH)
        @Email(message = ValidationMessages.INVALID_EMAIL)
        String email,

        @NotBlank(message = ValidationMessages.REQUIRED_PASSWORD)
        @Size(min = 8, message = ValidationMessages.PASSWORD_TOO_SHORT)
        @PasswordByteLength
        String password,

        @NotNull(message = ValidationMessages.REQUIRED_USER_ROLE)
        Role role
) {
}
