package com.lucas.bankingsystem.integration.address;

import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;

public interface AddressProvider {
    AddressLookupResponse findByPostalCode(String postalCode);
}
