package com.lucas.bankingsystem.exception.account;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidAccountDigitException extends BusinessException {
    public InvalidAccountDigitException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
