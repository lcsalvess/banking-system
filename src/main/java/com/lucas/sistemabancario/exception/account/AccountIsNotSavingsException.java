package com.lucas.sistemabancario.exception.account;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountIsNotSavingsException extends BusinessException {
    public AccountIsNotSavingsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
