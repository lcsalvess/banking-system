package com.lcsalvess.bankingsystem.exception.user;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SelfRoleChangeNotAllowedException extends BusinessException {

    public SelfRoleChangeNotAllowedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
