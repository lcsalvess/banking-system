package com.lucas.bankingsystem.controller;

import com.lucas.bankingsystem.dto.request.AccountRequestDTO;
import com.lucas.bankingsystem.dto.response.AccountResponseDTO;
import com.lucas.bankingsystem.dto.response.ErrorResponse;
import com.lucas.bankingsystem.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(
            summary = "Get all accounts",
            description = "Retrieves all registered accounts."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Accounts retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(implementation = AccountResponseDTO.class)
                            )
                    )
            )
    })
    @GetMapping
    public List<AccountResponseDTO> findAll() {
        return accountService.findAll();
    }

    @Operation(
            summary = "Get account by account number and digit",
            description = "Retrieves an account by its number and digit."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Account found successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description =  "Account digit invalid",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{accountNumber}")
    public AccountResponseDTO findByAccountNumber(
            @Parameter(
                    description = "Account number",
                    example = "12345",
                    required = true
            )
            @PathVariable String accountNumber,
            @Parameter(
                    description = "Account check digit",
                    example = "1",
                    required = true
            )
            @RequestParam String digit
    ) {
        return accountService.findByAccountNumber(accountNumber, digit);
    }

    @Operation(
            summary = "Create an account",
            description = "Creates a new account."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Account created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AccountResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid account data or request format",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Account already exists",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponseDTO create(@Valid @RequestBody AccountRequestDTO dto) {
        return accountService.create(dto);
    }

    @Operation(
            summary = "Cancel an account",
            description = "Cancels an existing account using its account number and check digit."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Account cancelled successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Account digit invalid",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Account already cancelled",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PatchMapping("/{accountNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @Parameter(
                    description = "Account number",
                    example = "12345",
                    required = true
            )
            @PathVariable String accountNumber,
            @Parameter(
                    description = "Account check digit",
                    example = "1",
                    required = true
            )
            @RequestParam String digit
    ) {
        accountService.cancel(accountNumber, digit);
    }
}
