package com.lucas.bankingsystem.integration.address;

import com.lucas.bankingsystem.integration.address.brasilapi.BrasilApiClient;
import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lucas.bankingsystem.integration.address.viacep.ViaCepClient;
import org.springframework.stereotype.Service;

@Service
public class AddressLookupService {

    private final AddressProvider viaCepClient;
    private final AddressProvider brasilApiClient;

    public AddressLookupService(
            ViaCepClient viaCepClient,
            BrasilApiClient brasilApiClient
    ) {
        this.viaCepClient = viaCepClient;
        this.brasilApiClient = brasilApiClient;
    }

    public AddressLookupResponse findByPostalCode(String postalCode) {
        try {
            return viaCepClient.findByPostalCode(postalCode);
        } catch (AddressProviderUnavailableException ex) {
            return brasilApiClient.findByPostalCode(postalCode);
        }
    }
}