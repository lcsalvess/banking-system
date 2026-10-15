package com.lcsalvess.bankingsystem.controller.security;

import com.lcsalvess.bankingsystem.dto.request.security.LoginRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.security.LoginResponseDTO;
import com.lcsalvess.bankingsystem.service.security.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Authentication",
        description = "Authentication and access token management."
)
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
            )
    })
    @SecurityRequirements
    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO dto) {
        return authService.authenticate(dto);
    }
}