package com.lcsalvess.bankingsystem.service.address;

import com.lcsalvess.bankingsystem.entity.address.State;

public record AddressData(
        String streetName,
        String neighborhood,
        String city,
        State state,
        String postalCode
) {
}
