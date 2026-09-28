package com.lucas.bankingsystem.integration.address;

import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.integration.address.viacep.ViaCepClient;
import org.springframework.stereotype.Service;

@Service
public class AddressLookupService {
    private final ViaCepClient viaCepClient;

    public AddressLookupService(ViaCepClient viaCepClient) {
        this.viaCepClient = viaCepClient;
    }

    public AddressLookupResponse findByPostalCode(String postalCode) {
        return viaCepClient.findByPostalCode(postalCode);
    }
}
