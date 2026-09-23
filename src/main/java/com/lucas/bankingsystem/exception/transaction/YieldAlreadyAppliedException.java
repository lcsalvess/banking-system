package com.lucas.bankingsystem.exception.transaction;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class YieldAlreadyAppliedException extends BusinessException {
    public YieldAlreadyAppliedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
