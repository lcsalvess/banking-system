package com.lucas.bankingsystem.integration.address.exception;

public class PostalCodeNotFoundException extends RuntimeException {
    public PostalCodeNotFoundException(String message) {
        super(message);
    }
}
