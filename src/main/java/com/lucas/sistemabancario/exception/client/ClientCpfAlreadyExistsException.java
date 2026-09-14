package com.lucas.sistemabancario.exception.client;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ClientCpfAlreadyExistsException extends BusinessException {
    public ClientCpfAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
