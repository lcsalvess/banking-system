package com.lcsalvess.bankingsystem.exception.transaction;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class BalanceLimitExceededException extends BusinessException {

    public BalanceLimitExceededException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}