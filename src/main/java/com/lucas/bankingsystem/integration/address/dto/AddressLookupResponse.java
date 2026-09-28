package com.lucas.bankingsystem.integration.address.dto;

public record AddressLookupResponse(
        String streetName,
        String neighborhood,
        String city,
        String state,
        String postalCode
) {
}
