package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserFullNameAlreadySetException extends BusinessException {

    public UserFullNameAlreadySetException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
