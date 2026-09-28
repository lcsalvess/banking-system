package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.AddressRequestDTO;
import com.lucas.bankingsystem.dto.request.AddressUpdateRequestDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.integration.address.AddressLookupService;
import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import org.springframework.stereotype.Service;

@Service
public class AddressService {

    private final AddressLookupService addressLookupService;

    public AddressService(AddressLookupService addressLookupService) {
        this.addressLookupService = addressLookupService;
    }

    public Address createFromPostalCode(AddressRequestDTO dto) {
        AddressLookupResponse addressData =
                addressLookupService.findByPostalCode(dto.postalCode());

        return new Address(
                addressData.streetName(),
                dto.streetNumber(),
                dto.complement(),
                addressData.neighborhood(),
                addressData.city(),
                State.valueOf(addressData.state()),
                addressData.postalCode().replace("-", "")
        );
    }

    public void updateFromPostalCode(Address address, AddressUpdateRequestDTO dto) {
        if (dto.postalCode() != null) {
            AddressLookupResponse addressData =
                    addressLookupService.findByPostalCode(dto.postalCode());

            address.setStreetName(addressData.streetName());
            address.setNeighborhood(addressData.neighborhood());
            address.setCity(addressData.city());
            address.setState(State.valueOf(addressData.state()));
            address.setPostalCode(addressData.postalCode().replace("-", ""));
        }

        address.update(dto);
    }
}