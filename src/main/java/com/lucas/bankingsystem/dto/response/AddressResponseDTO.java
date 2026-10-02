package com.lucas.bankingsystem.dto.response;

import com.lucas.bankingsystem.entity.Address;

public record AddressResponseDTO(
        String streetName,
        String streetNumber,
        String complement,
        String neighborhood,
        String city,
        String state,
        String postalCode
) {
    public static AddressResponseDTO fromEntity(Address address) {
        return new AddressResponseDTO(
                address.getStreetName(),
                address.getStreetNumber(),
                address.getComplement(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState().name(),
                address.getPostalCode()
        );
    }
}
