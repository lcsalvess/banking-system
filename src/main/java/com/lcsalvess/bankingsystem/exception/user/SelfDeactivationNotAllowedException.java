package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SelfDeactivationNotAllowedException extends BusinessException {

    public SelfDeactivationNotAllowedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}