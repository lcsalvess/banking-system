package com.lucas.sistemabancario.exception.client;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ClientNotFoundException extends BusinessException {
    public ClientNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
