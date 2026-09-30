package com.lucas.bankingsystem.integration.address;

import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
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

    @GetMapping("/lookup/{postalCode}")
    public AddressLookupResponse findByPostalCode(
            @PathVariable
            @Pattern(
                    regexp = "^\\d{8}$",
                    message = "O CEP deve conter exatamente 8 dígitos.")
            String postalCode) {
        return addressLookupService.findByPostalCode(postalCode);
    }
}