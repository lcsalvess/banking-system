package com.lcsalvess.bankingsystem.service.client;

import com.lcsalvess.bankingsystem.service.address.AddressData;

import java.util.Objects;

/**
 * Data already resolved for updating a client.
 *
 * <p>The address is optional and, when provided, always contains the result
 * of the postal code lookup, along with the supplied street number and complement.
 */
public record ClientUpdateData(
        String name,
        String email,
        String phoneNumber,
        AddressUpdateData address
) {

    public record AddressUpdateData(
            AddressData addressData,
            String streetNumber,
            String complement
    ) {
        public AddressUpdateData {
            Objects.requireNonNull(addressData, "addressData");
        }
    }
}
