package com.lucas.bankingsystem.exception.account;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountIsNotActiveException extends BusinessException {
    public AccountIsNotActiveException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
