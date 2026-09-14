package com.lucas.sistemabancario.exception.user;

import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserEmailAlreadyExistsException extends BusinessException {
    public UserEmailAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
