package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import org.springframework.http.HttpStatus;

public class SelfRoleChangeNotAllowedException extends BusinessException {

    public SelfRoleChangeNotAllowedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
