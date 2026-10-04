package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.AddressRequestDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.integration.address.AddressLookupService;
import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.service.address.AddressData;
import org.springframework.stereotype.Service;

@Service
public class AddressService {

    private final AddressLookupService addressLookupService;

    public AddressService(AddressLookupService addressLookupService) {
        this.addressLookupService = addressLookupService;
    }

    public Address createFromPostalCode(AddressRequestDTO dto) {
        AddressData addressData = findAddressByPostalCode(dto.postalCode());

        return new Address(
                addressData.streetName(),
                dto.streetNumber(),
                dto.complement(),
                addressData.neighborhood(),
                addressData.city(),
                addressData.state(),
                addressData.postalCode()
        );
    }

    public AddressData findAddressByPostalCode(String postalCode) {
        AddressLookupResponse response =
                addressLookupService.findByPostalCode(postalCode);

        return new AddressData(
                response.streetName(),
                response.neighborhood(),
                response.city(),
                State.valueOf(response.state()),
                response.postalCode()
        );
    }
}