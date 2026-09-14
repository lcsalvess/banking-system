package com.lucas.sistemabancario.exception.account;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountAlreadyExistsException extends BusinessException {
    public AccountAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
