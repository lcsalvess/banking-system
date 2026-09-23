package com.lucas.bankingsystem.exception.transaction;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidAmountException extends BusinessException {
    public InvalidAmountException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
