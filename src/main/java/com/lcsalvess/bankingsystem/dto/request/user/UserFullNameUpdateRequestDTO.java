package com.lcsalvess.bankingsystem.dto.request.user;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import com.lcsalvess.bankingsystem.validation.fullname.ValidFullName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UserFullNameUpdateRequestDTO(

        @Schema(
                description = "Full name to populate on a legacy user account.",
                example = "Jane Michael Doe",
                maxLength = 150
        )
        @NotBlank(message = ValidationMessages.REQUIRED_FULL_NAME)
        @ValidFullName
        String fullName
) {
}
