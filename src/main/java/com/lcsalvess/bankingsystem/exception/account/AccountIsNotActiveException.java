package com.lcsalvess.bankingsystem.exception.account;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountIsNotActiveException extends BusinessException {
    public AccountIsNotActiveException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
