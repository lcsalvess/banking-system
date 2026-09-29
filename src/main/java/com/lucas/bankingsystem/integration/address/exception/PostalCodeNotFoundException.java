package com.lucas.bankingsystem.integration.address.exception;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PostalCodeNotFoundException extends BusinessException {

    public PostalCodeNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
