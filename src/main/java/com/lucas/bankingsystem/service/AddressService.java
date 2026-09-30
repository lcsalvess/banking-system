package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.AddressRequestDTO;
import com.lucas.bankingsystem.dto.request.AddressUpdateRequestDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.integration.address.AddressLookupService;
import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AddressService {

    private static final Logger log = LoggerFactory.getLogger(AddressService.class);

    private final AddressLookupService addressLookupService;

    public AddressService(AddressLookupService addressLookupService) {
        this.addressLookupService = addressLookupService;
    }

    public Address createFromPostalCode(AddressRequestDTO dto) {
        AddressLookupResponse addressData =
                addressLookupService.findByPostalCode(dto.postalCode());

        Address address = new Address(
                addressData.streetName(),
                dto.streetNumber(),
                dto.complement(),
                addressData.neighborhood(),
                addressData.city(),
                State.valueOf(addressData.state()),
                addressData.postalCode().replace("-", "")
        );

        log.info("Address successfully created from postal code");

        return address;
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

        log.info("Address successfully updated");
    }
}