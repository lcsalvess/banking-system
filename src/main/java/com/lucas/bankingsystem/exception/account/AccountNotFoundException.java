package com.lucas.bankingsystem.exception.account;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
