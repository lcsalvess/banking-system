package com.lucas.sistemabancario.exception.transaction;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class YieldNotAvailableException extends BusinessException {
    public YieldNotAvailableException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
