package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SystemUserRoleCannotBeChangedException extends BusinessException {

    public SystemUserRoleCannotBeChangedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
