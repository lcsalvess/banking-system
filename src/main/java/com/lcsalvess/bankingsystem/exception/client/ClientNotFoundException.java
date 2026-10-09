package com.lcsalvess.bankingsystem.exception.client;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import org.springframework.http.HttpStatus;

public class ClientNotFoundException extends BusinessException {

    public ClientNotFoundException() {
        super(ApiErrorMessages.CLIENT_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public ClientNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
