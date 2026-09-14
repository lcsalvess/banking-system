package com.lucas.sistemabancario.exception.account;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountIsNotActiveException extends BusinessException {
    public AccountIsNotActiveException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
