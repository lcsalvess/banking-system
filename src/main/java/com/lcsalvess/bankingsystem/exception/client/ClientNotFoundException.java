package com.lcsalvess.bankingsystem.exception.client;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ClientNotFoundException extends BusinessException {
    private static final String DEFAULT_MESSAGE = "Cliente não encontrado.";

    public ClientNotFoundException() {
        super(DEFAULT_MESSAGE, HttpStatus.NOT_FOUND);
    }

    public ClientNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
