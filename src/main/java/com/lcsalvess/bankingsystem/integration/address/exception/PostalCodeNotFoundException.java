package com.lcsalvess.bankingsystem.integration.address.exception;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PostalCodeNotFoundException extends BusinessException {

    public PostalCodeNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
