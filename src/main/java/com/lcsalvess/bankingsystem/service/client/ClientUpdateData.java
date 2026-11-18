package com.lcsalvess.bankingsystem.service.client;

import com.lcsalvess.bankingsystem.service.address.AddressData;
import java.util.Objects;

/**
 * Contains data already resolved for updating a client.
 * <p>
 * The address is optional and, when provided, contains the result
 * of the postal code lookup, along with the supplied street number
 * and complement.
 *
 * @param name the updated client name, or {@code null} to keep the current value
 * @param email the updated email address, or {@code null} to keep the current value
 * @param phoneNumber the updated phone number, or {@code null} to keep the current value
 * @param address the resolved address update data, or {@code null} when the address
 *                should remain unchanged
 */
public record ClientUpdateData(
    String name,
    String email,
    String phoneNumber,
    AddressUpdateData address
) {

  /**
   * Contains the resolved address data and additional address information.
   *
   * @param addressData the address information resolved from the postal code
   * @param streetNumber the street number supplied for the address
   * @param complement the optional address complement
   */
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
