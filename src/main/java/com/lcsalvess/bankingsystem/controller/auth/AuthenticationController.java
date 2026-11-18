package com.lcsalvess.bankingsystem.controller.auth;

import com.lcsalvess.bankingsystem.dto.request.security.ChangePasswordRequestDto;
import com.lcsalvess.bankingsystem.dto.request.security.LoginRequestDto;
import com.lcsalvess.bankingsystem.dto.response.security.LoginResponseDto;
import com.lcsalvess.bankingsystem.service.security.AuthenticationService;
import com.lcsalvess.bankingsystem.service.security.PasswordChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
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

/**
 * REST controller responsible for user authentication and password management.
 */
@Tag(
    name = "Authentication",
    description = "Authentication and credential management."
)
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

  private final AuthenticationService authService;
  private final PasswordChangeService passwordChangeService;

  public AuthenticationController(
      AuthenticationService authService,
      PasswordChangeService passwordChangeService
  ) {
    this.authService = authService;
    this.passwordChangeService = passwordChangeService;
  }

  /**
   * Authenticates a user using their credentials and returns an access token.
   *
   * @param dto the login credentials
   * @param request the HTTP request used to obtain the client's remote address
   * @return the authentication response containing the access token
   */
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
          responseCode = "401",
          description = "Invalid username or password."
      ),
      @ApiResponse(
          responseCode = "429",
          description = "Too many login attempts. Retry after the indicated delay.",
          headers = @Header(
              name = "Retry-After",
              description = "Number of seconds to wait before retrying.",
              schema = @Schema(
                  type = "integer",
                  format = "int64",
                  example = "300"
              )
          )
      )
  })
  @SecurityRequirements
  @PostMapping("/login")
  public LoginResponseDto login(
      @Valid @RequestBody LoginRequestDto dto,
      HttpServletRequest request
  ) {
    return authService.authenticate(dto, request.getRemoteAddr());
  }

  /**
   * Changes the authenticated user's password and invalidates previously issued tokens.
   *
   * @param dto the password change request containing the current and new passwords
   */
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
      ),
      @ApiResponse(
          responseCode = "429",
          description = "Too many failed password change attempts. "
              + "Retry after the indicated delay.",
          headers = @Header(
              name = "Retry-After",
              description = "Number of seconds to wait before retrying.",
              schema = @Schema(
                  type = "integer",
                  format = "int64",
                  example = "900"
              )
          )
      )
  })
  @PatchMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@Valid @RequestBody ChangePasswordRequestDto dto) {
    passwordChangeService.changePassword(dto);
  }
}
