package com.lcsalvess.bankingsystem.integration.address.exception;

public class AddressProviderUnavailableException extends RuntimeException {

    public AddressProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AddressProviderUnavailableException(String message) {
        super(message);
    }
}