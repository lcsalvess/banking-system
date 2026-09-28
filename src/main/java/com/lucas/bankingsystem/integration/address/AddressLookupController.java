package com.lucas.bankingsystem.integration.address;

import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/addresses")
public class AddressLookupController {

    private final AddressLookupService addressLookupService;

    public AddressLookupController(AddressLookupService addressLookupService) {
        this.addressLookupService = addressLookupService;
    }

    @GetMapping("/lookup")
    public AddressLookupResponse findByPostalCode(
            @RequestParam String postalCode) {
        return addressLookupService.findByPostalCode(postalCode);
    }
}