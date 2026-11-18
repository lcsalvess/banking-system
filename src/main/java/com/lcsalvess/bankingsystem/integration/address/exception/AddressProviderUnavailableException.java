package com.lcsalvess.bankingsystem.integration.address.exception;

/**
 * Indicates that an external address provider is unavailable or cannot complete
 * an address lookup request.
 */
public class AddressProviderUnavailableException extends RuntimeException {

  public AddressProviderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public AddressProviderUnavailableException(String message) {
    super(message);
  }
}
