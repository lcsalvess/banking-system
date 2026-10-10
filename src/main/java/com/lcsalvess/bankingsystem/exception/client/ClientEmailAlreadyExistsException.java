package com.lcsalvess.bankingsystem.exception.client;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ClientEmailAlreadyExistsException extends BusinessException {

    public ClientEmailAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public ClientEmailAlreadyExistsException(String message, Throwable cause) {
        super(message, HttpStatus.CONFLICT, cause);
    }
}