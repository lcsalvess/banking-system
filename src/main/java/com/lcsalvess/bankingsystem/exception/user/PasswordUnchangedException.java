package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PasswordUnchangedException extends BusinessException {

    public PasswordUnchangedException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
