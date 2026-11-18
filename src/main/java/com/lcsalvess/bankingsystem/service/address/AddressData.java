package com.lcsalvess.bankingsystem.service.address;

import com.lcsalvess.bankingsystem.entity.address.State;

/**
 * Represents normalized address information retrieved from a postal code lookup.
 *
 * @param streetName street name
 * @param neighborhood neighborhood name
 * @param city city name
 * @param state Brazilian state
 * @param postalCode postal code
 */
public record AddressData(
    String streetName,
    String neighborhood,
    String city,
    State state,
    String postalCode
) {
}
