package com.lcsalvess.bankingsystem.exception.transaction;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

public class BalanceLimitExceededException extends BusinessException {

    public BalanceLimitExceededException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}