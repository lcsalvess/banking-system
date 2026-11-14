package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidCurrentPasswordException extends BusinessException {

    public InvalidCurrentPasswordException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}