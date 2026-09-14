package com.lucas.sistemabancario.exception.account;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountHasBalanceException extends BusinessException {
    public AccountHasBalanceException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
