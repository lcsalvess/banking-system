package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

public class UsernameAlreadyExistsException extends BusinessException {
    public UsernameAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
