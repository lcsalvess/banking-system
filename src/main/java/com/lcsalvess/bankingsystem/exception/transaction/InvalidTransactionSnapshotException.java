package com.lcsalvess.bankingsystem.exception.transaction;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidTransactionSnapshotException extends BusinessException {

    public InvalidTransactionSnapshotException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}