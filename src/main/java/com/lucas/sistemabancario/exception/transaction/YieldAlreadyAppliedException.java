package com.lucas.sistemabancario.exception.transaction;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class YieldAlreadyAppliedException extends BusinessException {
    public YieldAlreadyAppliedException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
