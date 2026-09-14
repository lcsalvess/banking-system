package com.lucas.sistemabancario.exception.account;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidAccountTypeException extends BusinessException {
    public InvalidAccountTypeException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
