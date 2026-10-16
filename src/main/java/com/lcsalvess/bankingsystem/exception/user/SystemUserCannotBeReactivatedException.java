package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SystemUserCannotBeReactivatedException
        extends BusinessException {

    public SystemUserCannotBeReactivatedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}