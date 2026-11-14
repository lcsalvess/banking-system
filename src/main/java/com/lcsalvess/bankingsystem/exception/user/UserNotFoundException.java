package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}