package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidAccountDigitException extends BusinessException {
    public InvalidAccountDigitException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
