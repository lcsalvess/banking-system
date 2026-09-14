package com.lucas.sistemabancario.exception.account;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountsAreSameException extends BusinessException {
    public AccountsAreSameException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
