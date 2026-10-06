package com.lcsalvess.bankingsystem.exception.transaction;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class YieldAlreadyAppliedException extends BusinessException {
    public YieldAlreadyAppliedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
