package com.lcsalvess.bankingsystem.service.address;

import com.lcsalvess.bankingsystem.dto.request.address.AddressRequestDto;
import com.lcsalvess.bankingsystem.entity.address.Address;
import com.lcsalvess.bankingsystem.entity.address.State;
import com.lcsalvess.bankingsystem.integration.address.AddressLookupService;
import com.lcsalvess.bankingsystem.integration.address.dto.AddressLookupResponse;
import org.springframework.stereotype.Service;

/**
 * Coordinates address creation and postal code lookups through the address integration.
 */
@Service
public class AddressService {

  private final AddressLookupService addressLookupService;

  public AddressService(AddressLookupService addressLookupService) {
    this.addressLookupService = addressLookupService;
  }

  /**
   * Creates an address using postal code data and the additional information provided.
   *
   * @param dto address request containing the postal code, street number, and complement
   * @return the created address
   */
  public Address createFromPostalCode(AddressRequestDto dto) {
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

  /**
   * Retrieves address information using a postal code and maps the integration response
   * to the application's address data representation.
   *
   * @param postalCode postal code used to search for address information
   * @return address data obtained from the lookup service
   */
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
