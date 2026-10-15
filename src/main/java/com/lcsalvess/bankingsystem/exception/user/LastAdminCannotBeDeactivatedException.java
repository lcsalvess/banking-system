package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class LastAdminCannotBeDeactivatedException extends BusinessException {

    public LastAdminCannotBeDeactivatedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}