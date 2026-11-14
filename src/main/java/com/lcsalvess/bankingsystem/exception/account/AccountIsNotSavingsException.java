package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountIsNotSavingsException extends BusinessException {
    public AccountIsNotSavingsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
