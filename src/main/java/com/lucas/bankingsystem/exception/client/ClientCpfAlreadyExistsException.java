package com.lucas.bankingsystem.exception.client;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ClientCpfAlreadyExistsException extends BusinessException {
    public ClientCpfAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
