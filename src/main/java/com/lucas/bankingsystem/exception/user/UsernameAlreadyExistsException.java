package com.lucas.bankingsystem.exception.user;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UsernameAlreadyExistsException extends BusinessException {
    public UsernameAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
