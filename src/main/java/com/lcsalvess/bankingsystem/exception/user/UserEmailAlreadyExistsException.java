package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserEmailAlreadyExistsException extends BusinessException {
    public UserEmailAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
