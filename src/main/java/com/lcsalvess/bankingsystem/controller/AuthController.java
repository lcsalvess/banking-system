package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.request.security.ChangePasswordRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.security.LoginRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.security.LoginResponseDTO;
import com.lcsalvess.bankingsystem.service.security.AuthService;
import com.lcsalvess.bankingsystem.service.security.PasswordChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Authentication",
        description = "Authentication and access token management."
)
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordChangeService passwordChangeService;

    public AuthController(
            AuthService authService,
            PasswordChangeService passwordChangeService
    ) {
        this.authService = authService;
        this.passwordChangeService = passwordChangeService;
    }

    @Operation(
            summary = "Authenticate a user",
            description = "Authenticates a user with valid credentials and returns "
                    + "an access token for subsequent authenticated requests."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authentication successful."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body or validation errors."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many login attempts. Retry after the indicated delay."
            )
    })
    @SecurityRequirements
    @PostMapping("/login")
    public LoginResponseDTO login(
            @Valid @RequestBody LoginRequestDTO dto,
            HttpServletRequest request
    ) {
        return authService.authenticate(dto, request.getRemoteAddr());
    }

    @Operation(
            summary = "Change the authenticated user's password",
            description = "Changes the password of the authenticated user after confirming "
                    + "the current one. All tokens issued before the change, including the "
                    + "one used in this request, stop being valid, so the user must "
                    + "authenticate again."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Password changed successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body, incorrect current password, "
                            + "or new password equal to the current one."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or revoked access token."
            )
    })
    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequestDTO dto) {
        passwordChangeService.changePassword(dto);
    }
}