package com.lucas.bankingsystem.exception.account;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountAlreadyExistsException extends BusinessException {
    public AccountAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
