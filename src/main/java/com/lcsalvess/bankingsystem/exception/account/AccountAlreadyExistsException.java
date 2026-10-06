package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountAlreadyExistsException extends BusinessException {
    public AccountAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
