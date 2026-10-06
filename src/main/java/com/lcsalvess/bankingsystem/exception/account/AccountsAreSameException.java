package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountsAreSameException extends BusinessException {
    public AccountsAreSameException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
