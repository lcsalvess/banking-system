package com.lucas.bankingsystem.service.address;

import com.lucas.bankingsystem.entity.enums.State;

public record AddressData(
        String streetName,
        String neighborhood,
        String city,
        State state,
        String postalCode
) {
}
