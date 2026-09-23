package com.lucas.bankingsystem.exception.client;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ClientNotFoundException extends BusinessException {
    public ClientNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
