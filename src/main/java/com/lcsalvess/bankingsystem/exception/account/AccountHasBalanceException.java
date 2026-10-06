package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountHasBalanceException extends BusinessException {
    public AccountHasBalanceException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
