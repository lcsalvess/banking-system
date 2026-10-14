package com.lcsalvess.bankingsystem.exception.pagination;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidSortPropertyException extends BusinessException {
    public InvalidSortPropertyException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}