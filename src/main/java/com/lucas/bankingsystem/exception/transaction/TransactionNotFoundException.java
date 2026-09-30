package com.lucas.bankingsystem.exception.transaction;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends BusinessException {
    public TransactionNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
