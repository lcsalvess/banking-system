package com.lucas.bankingsystem.integration.address.exception;

public class AddressProviderUnavailableException extends RuntimeException {

    public AddressProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}