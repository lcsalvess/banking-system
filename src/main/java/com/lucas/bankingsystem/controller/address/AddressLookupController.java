package com.lucas.bankingsystem.controller.address;

import com.lucas.bankingsystem.dto.response.exception.ErrorResponse;
import com.lucas.bankingsystem.dto.response.exception.ValidationErrorResponse;
import com.lucas.bankingsystem.integration.address.AddressLookupService;
import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/addresses")
@Validated
public class AddressLookupController {

    private final AddressLookupService addressLookupService;

    public AddressLookupController(AddressLookupService addressLookupService) {
        this.addressLookupService = addressLookupService;
    }

    @Operation(
            summary = "Look up an address by postal code",
            description = "Retrieves address information using the postal code. "
                    + "ViaCEP is the primary provider, with BrasilAPI as a fallback."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Address found successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AddressLookupResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid postal code. It must contain exactly 8 digits.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Address providers are unavailable.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/lookup/{postalCode}")
    public AddressLookupResponse findByPostalCode(
            @Parameter(
                    name = "postalCode",
                    description = "Postal code containing exactly 8 digits, without a hyphen.",
                    required = true,
                    example = "01001000"
            )
            @PathVariable
            @Pattern(
                    regexp = "^\\d{8}$",
                    message = "O CEP deve conter exatamente 8 dígitos.")
            String postalCode) {
        return addressLookupService.findByPostalCode(postalCode);
    }
}