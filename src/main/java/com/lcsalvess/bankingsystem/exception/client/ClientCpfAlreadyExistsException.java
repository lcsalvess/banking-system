package com.lcsalvess.bankingsystem.exception.client;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ClientCpfAlreadyExistsException extends BusinessException {

    public ClientCpfAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public ClientCpfAlreadyExistsException(
            String message,
            Throwable cause
    ) {
        super(message, HttpStatus.CONFLICT, cause);
    }
}
