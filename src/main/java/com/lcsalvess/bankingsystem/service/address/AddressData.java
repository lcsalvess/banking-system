package com.lcsalvess.bankingsystem.service.address;

import com.lcsalvess.bankingsystem.entity.enums.State;

public record AddressData(
        String streetName,
        String neighborhood,
        String city,
        State state,
        String postalCode
) {
}
