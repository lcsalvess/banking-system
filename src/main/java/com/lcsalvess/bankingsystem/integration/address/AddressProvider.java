package com.lcsalvess.bankingsystem.integration.address;

import com.lcsalvess.bankingsystem.integration.address.dto.AddressLookupResponse;

/**
 * Defines the contract for external address lookup providers.
 */
public interface AddressProvider {

  /**
   * Retrieves address information using a postal code.
   *
   * @param postalCode postal code used to search for address information
   * @return address information retrieved from the provider
   */
  AddressLookupResponse findByPostalCode(String postalCode);
}
