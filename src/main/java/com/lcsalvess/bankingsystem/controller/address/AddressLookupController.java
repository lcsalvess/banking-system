package com.lcsalvess.bankingsystem.controller.address;

import com.lcsalvess.bankingsystem.dto.response.AddressLookupResponseDTO;
import com.lcsalvess.bankingsystem.service.address.AddressService;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Tag(
        name = "Address Lookup",
        description = "Address lookup by Brazilian postal code."
)
@PreAuthorize("hasRole('EMPLOYEE')")
@RestController
@RequestMapping("/api/v1/addresses")
@Validated
public class AddressLookupController {

    private final AddressService addressService;

    public AddressLookupController(AddressService addressService) {
        this.addressService = addressService;
    }

    @Operation(
            summary = "Look up an address by postal code",
            description = "Retrieves address information using the postal code. "
                    + "ViaCEP is the primary provider, with BrasilAPI as a fallback."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Address found successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid postal code. It must contain exactly 8 digits."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Postal code not found."
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Address providers are unavailable."
            )
    })
    @GetMapping("/lookup/{postalCode}")
    public AddressLookupResponseDTO findByPostalCode(
            @Parameter(
                    name = "postalCode",
                    description = "Postal code containing exactly 8 digits, without a hyphen.",
                    required = true,
                    example = "01001000"
            )
            @PathVariable("postalCode")
            @Pattern(
                    regexp = "^\\d{8}$",
                    message = ValidationMessages.INVALID_POSTAL_CODE_FORMAT
            )
            String postalCode) {
        return addressService.lookupByPostalCode(postalCode);
    }
}