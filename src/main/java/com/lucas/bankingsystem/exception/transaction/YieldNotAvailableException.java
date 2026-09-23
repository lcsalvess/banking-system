package com.lucas.bankingsystem.exception.transaction;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class YieldNotAvailableException extends BusinessException {
    public YieldNotAvailableException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
