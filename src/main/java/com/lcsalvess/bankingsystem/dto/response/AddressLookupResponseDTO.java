package com.lcsalvess.bankingsystem.dto.response;

public record AddressLookupResponseDTO(
        String streetName,
        String neighborhood,
        String city,
        String state,
        String postalCode
) {
}
