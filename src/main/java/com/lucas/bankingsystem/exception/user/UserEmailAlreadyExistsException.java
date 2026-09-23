package com.lucas.bankingsystem.exception.user;

import com.lucas.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserEmailAlreadyExistsException extends BusinessException {
    public UserEmailAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
