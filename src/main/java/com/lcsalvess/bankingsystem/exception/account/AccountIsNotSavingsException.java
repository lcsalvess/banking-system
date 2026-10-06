package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountIsNotSavingsException extends BusinessException {
    public AccountIsNotSavingsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
