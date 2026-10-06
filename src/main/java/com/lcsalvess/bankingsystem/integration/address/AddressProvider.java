package com.lcsalvess.bankingsystem.integration.address;

import com.lcsalvess.bankingsystem.integration.address.dto.AddressLookupResponse;

public interface AddressProvider {
    AddressLookupResponse findByPostalCode(String postalCode);
}
