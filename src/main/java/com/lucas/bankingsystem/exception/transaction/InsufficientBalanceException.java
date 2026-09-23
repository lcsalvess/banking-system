package com.lucas.bankingsystem.exception.transaction;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends BusinessException {
    public InsufficientBalanceException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
